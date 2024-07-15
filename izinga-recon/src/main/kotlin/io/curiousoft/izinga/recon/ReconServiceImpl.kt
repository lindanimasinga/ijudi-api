package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.order.OrderRepository
import io.curiousoft.izinga.commons.payout.events.OrderPayoutEvent
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.PayoutBundleRepo
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.tips.TipsService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.*

@Service
class ReconServiceImpl(
    private val orderRepo: OrderRepository,
    private val payoutBundleRepo: PayoutBundleRepo,
    private val storeRepo: StoreRepository,
    private val userProfileRepo: UserProfileRepo,
    private val payoutRepo: PayoutRepository,
    private val tipsService: TipsService,
    private val applicationEventPublisher: ApplicationEventPublisher
) : ReconService {

    override fun generateNextPayoutsToShop(): PayoutBundle? {
        return orderRepo.findByShopPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID)
            ?.groupBy { it.shopId }
            ?.map { map ->  storeRepo.findByIdOrNull(map.key)
                ?.takeIf { it.hasPaymentAgreement }
                ?.let {
                    ShopPayout(
                        toId = it.id!!,
                        toName = it.name!!,
                        toBankName = it.bank?.name!!,
                        toAccountNumber = it.bank?.accountId?.replace("+27", "0")!!,
                        toType = it.bank?.type!!,
                        orders = map.value.toMutableSet(),
                        toBranchCode = it.bank?.branchCode!!,
                        toReference = "iZinga pay ${map.value.last().id}",
                        fromReference = "Payment to ${it.name}",
                        emailAddress = it.emailAddress,
                        emailNotify = "",
                        emailSubject = "iZinga pay ${map.value.last().id}")
                }
            }
            ?.filterNotNull()
            ?.toList()
            ?.let {
                val bundle =
                    payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.SHOP)?.apply { payouts = it } ?: PayoutBundle(
                        payouts = it,
                        createdBy = "System",
                        type = PayoutType.SHOP
                    )
                payoutBundleRepo.save(bundle)
            }
    }

    override fun generateNextPayoutsToMessenger(): PayoutBundle? {
        val bundle = payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.MESSENGER)
        val tips = tipsService.getTodayTips()
        return orderRepo.findByMessengerPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID)
            ?.filter { it.shippingData?.messengerId?.isNotEmpty() ?: false }
            ?.groupBy { it.shippingData?.messengerId }
            ?.map { map ->
                userProfileRepo.findByIdOrNull(map.key)?.let { messng ->
                    bundle?.payouts?.firstOrNull { it.toId == messng.id }?.also { payout ->
                        payout.emailSent = payout.emailSent && payout.orders.size ==  map.value.toMutableSet().size
                        payout.orders = map.value.toMutableSet()
                        tips?.filter { messng.emailAddress == it.emailAddress }
                            ?.toMutableSet()
                            ?.also { payout.tips?.addAll(it) }
                    } ?: MessengerPayout(
                        toId = messng.id!!,
                        toName = messng.name!!,
                        toBankName = messng.bank?.name!!,
                        toType = messng.bank?.type!!,
                        toAccountNumber = messng.bank?.accountId?.replace("+27", "0")!!,
                        orders = map.value.toMutableSet(),
                        toBranchCode = messng.bank!!.branchCode!!,
                        toReference = "iZinga pay ${map.value.last().id}",
                        fromReference = "Payment to ${messng.name}",
                        tips = tips?.filter { messng.emailAddress == it.emailAddress }?.toMutableSet(),
                        emailAddress = messng.emailAddress!!,
                        emailNotify = "",
                        emailSubject = "iZinga pay ${map.value.last().id}"
                    ).also {
                        it.isPermEmployed = messng.isPermanentEmployed ?: false
                    }
                }
            }?.filterNotNull()
            ?.filter { it.total > 0.toBigDecimal() }
            ?.toList()
            ?.let {
                val bundle = bundle?.let { b -> b.payouts = it; b } ?: PayoutBundle(
                    payouts = it,
                    createdBy = "System",
                    type = PayoutType.MESSENGER
                )
                payoutBundleRepo.save(bundle)
            }
    }

    override fun updatePayoutStatus(bundleResponse: PayoutBundleResults): PayoutBundle? {
        return payoutBundleRepo.findByIdOrNull(bundleResponse.bundleId)
            ?.also { payoutBundle ->
            val successfulPayouts = bundleResponse.payoutItemResults?.map { payResults ->
                payoutBundle.payouts.firstOrNull { payResults.toId == it.toId }?.apply { paid = payResults.paid }
            }?.filter { it?.paid ?: false }
                ?.filterNotNull()
                ?: payoutBundle.payouts.onEach { it.paid = true }

            successfulPayouts
                .flatMap { it.orders }
                .map {
                    OrderPayoutEvent(
                        this, it.id!!,
                        it.shopPaid || payoutBundle.type == PayoutType.SHOP,
                        it.messengerPaid || payoutBundle.type == PayoutType.MESSENGER
                    )
                }.forEach { applicationEventPublisher.publishEvent(it) }

        }?.let {
            it.executed = true
            payoutBundleRepo.save(it)
        }
    }

    override fun updateBundle(bundle: PayoutBundle) : PayoutBundle {
        return payoutBundleRepo.save(bundle)
    }

    override fun getAllPayoutBundles(payoutType: PayoutType, fromDate: Date, toDate: Date): List<PayoutBundle> {
        return payoutBundleRepo.findByCreatedDateBetweenAndType(fromDate, toDate, payoutType)
    }

    override fun getAllPayouts(payoutType: PayoutType, fromDate: Date, toDate: Date, toId: String): List<Payout> =
        getAllPayoutBundles(payoutType, fromDate, toDate)
            .filter { it.executed }
            .flatMap { it.payouts }
            .filter { it.toId == toId }

    override fun findPayout(bundleId: String, payoutId: String): Payout? =
        payoutRepo.findByIdOrNull(bundleId)?.let { it.payouts.firstOrNull { a -> a.toId == payoutId } }
}