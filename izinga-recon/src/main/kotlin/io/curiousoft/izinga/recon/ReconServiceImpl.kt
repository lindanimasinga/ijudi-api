package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.payout.events.OrderPayoutEvent
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.*

@Service
class ReconServiceImpl(
    private val storeRepo: StoreRepository,
    private val userProfileRepo: UserProfileRepo,
    private val shopPayoutRepo: ShopPayoutRepository,
    private val messengerPayoutRepository: MessengerPayoutRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) : ReconService {

    private val logger = LoggerFactory.getLogger(ReconServiceImpl::class.java)

    @Async
    override fun generatePayoutForShopAndOrder(order: Order): ShopPayout? {
        if (order.stage != OrderStage.STAGE_7_ALL_PAID) {
            logger.info("no payout generated for order {} at stage STAGE_7_ALL_PAID", order.id)
            return null
        }

        val store = storeRepo.findByIdOrNull(order.shopId);
        if (store?.hasPaymentAgreement == false) {
            logger.info("no payout created because store {} has no payout agreement", store.id);
            return null
        }

        val payout = shopPayoutRepo.findByToIdAndPayoutStage(order.shopId, PayoutStage.PENDING) ?: ShopPayout(
            toId = store?.id!!,
            toName = store.name!!,
            toBankName = store.bank?.name!!,
            toType = store.bank?.type!!,
            toAccountNumber = store.bank?.accountId?.replace("+27", "0")!!,
            orders = mutableSetOf(),
            toBranchCode = store.bank?.branchCode!!,
            fromReference = "Payment to ${store.name}",
            toReference = "iZinga pay",
            emailNotify = "",
            emailAddress = store.emailAddress,
            emailSubject = "iZinga pay",
        )

        payout.orders.add(order)
        payout.emailSent = false
        return shopPayoutRepo.save(payout)
    }

    override fun generatePayoutForMessengerAndOrder(order: Order): MessengerPayout? {
        val messng = userProfileRepo.findByIdOrNull(order.shippingData?.messengerId)
        if (messng?.isPermanentEmployed == true && order.tip?.let { it <= 0 } == true) {
            return null
        }

        //val tips = tipsService.getTodayTips()
        val payout = messengerPayoutRepository.findByToIdAndPayoutStage(messng?.id!!, PayoutStage.PENDING) ?: MessengerPayout(
            toId = messng.id!!,
            toName = messng.name!!,
            toBankName = messng.bank?.name!!,
            toType = messng.bank?.type!!,
            toAccountNumber = messng.bank?.accountId?.replace("+27", "0")!!,
            orders = mutableSetOf(),
            toBranchCode = messng.bank!!.branchCode!!,
            toReference = "iZinga pay",
            fromReference = "Payment to ${messng.name}",
            tips = mutableSetOf(),
            emailAddress = messng.emailAddress!!,
            emailNotify = "",
            emailSubject = "iZinga pay"
        ).also {
            it.isPermEmployed = messng.isPermanentEmployed ?: false
        }

        payout.orders.add(order)
        payout.emailSent = false
        return messengerPayoutRepository.save(payout)
    }

    override fun updatePayoutStatus(bundleResponse: PayoutBundleResults) {
        bundleResponse.payoutItemResults
            .filter { it.type == PayoutType.SHOP }
            .mapNotNull {
                shopPayoutRepo.findByToIdAndPayoutStage(it.toId, PayoutStage.PROCESSING)
                    ?.let { payout ->
                        payout.paid = it.paid
                        payout.bundleId = bundleResponse.bundleId
                        shopPayoutRepo.save(payout)
                        payout
                    }
            }
            .filter { it.paid }
            .onEach {
                it.payoutStage = PayoutStage.COMPLETED
                shopPayoutRepo.save(it)
            }
            .flatMap { it.orders }
            .forEach {
                val payoutEvent = OrderPayoutEvent(
                    source = this,
                    orderId = it.id!!,
                    isStorePaid = true
                )
                applicationEventPublisher.publishEvent(payoutEvent)
            }

        bundleResponse.payoutItemResults
            .filter { it.type == PayoutType.MESSENGER }
            .mapNotNull {
                messengerPayoutRepository.findByToIdAndPayoutStage(it.toId, PayoutStage.PROCESSING)
                    ?.let { payout ->
                        payout.paid = it.paid
                        messengerPayoutRepository.save(payout)
                        payout
                    }
            }
            .filter { it.paid }
            .onEach {
                it.payoutStage = PayoutStage.COMPLETED
                messengerPayoutRepository.save(it)
            }
            .flatMap { it.orders }
            .forEach {
                val event  = OrderPayoutEvent(
                    source = this,
                    orderId = it.id!!,
                    isMessengerPaid = true)
                applicationEventPublisher.publishEvent(event)
            }
    }

    override fun updateBundle(bundle: PayoutBundle) {
        return bundle.payouts.groupBy { it.javaClass.name }
            .forEach( {
                when (it.key) {
                    ShopPayout::class.qualifiedName -> shopPayoutRepo.saveAll(it.value as List<ShopPayout>)
                    MessengerPayout::class.qualifiedName -> messengerPayoutRepository.saveAll(it.value as List<MessengerPayout>)
                }
        })
    }

    override fun getAllPayouts(payoutType: PayoutType, from: Date, toDate: Date, toId: String): List<Payout> {
        return when (payoutType) {
            PayoutType.SHOP -> shopPayoutRepo.findByModifiedDateBetweenAndToId(from, toDate, toId)
            PayoutType.MESSENGER -> messengerPayoutRepository.findByModifiedDateAndToId(from, toDate, toId)
        }
    }

    override fun getAllPayoutBundles(payoutType: PayoutType, from: Date, toDate: Date): List<Payout> {
        return when (payoutType) {
            PayoutType.SHOP -> shopPayoutRepo.findByModifiedDateBetween(from, toDate)
            PayoutType.MESSENGER -> messengerPayoutRepository.findByModifiedDateBetween(from, toDate)
        }
    }

    override fun getCurrentPayoutBundleForShops(): PayoutBundle {
        return PayoutBundle(payouts = shopPayoutRepo.findByPayoutStage(),
            type = PayoutType.SHOP,
            createdBy = "izinga-system")
    }

    override fun getCurrentPayoutBundleForMessenger(): PayoutBundle {
        return PayoutBundle(payouts = messengerPayoutRepository.findByPayoutStage(),
            type = PayoutType.MESSENGER,
            createdBy = "izinga-system")
    }

    override fun findPayout(bundleId: String, payoutId: String): Payout? = shopPayoutRepo.findByIdOrNull(payoutId)
        ?: messengerPayoutRepository.findByIdOrNull(payoutId)

}