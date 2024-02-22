package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.repo.OrderRepository
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.PayoutBundleRepo
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.tips.TipsService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.*

@Service
class ReconServiceImpl(
    private val orderRepo: OrderRepository,
    private val payoutBundleRepo: PayoutBundleRepo,
    private val storeRepo: StoreRepository,
    private val messengerRepo: UserProfileRepo,
    private val payoutRepo: PayoutRepository,
    private val tipsService: TipsService
) : ReconService {

    override fun generateNextPayoutsToShop(): PayoutBundle? {
        return orderRepo.findByShopPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID)
            ?.groupBy { it.shopId }
            ?.map { map ->
                storeRepo.findByIdOrNull(map.key)?.let {
                    ShopPayout(
                    toId = map.key, toName = it.name!!, toBankName = it.bank?.name!!, toAccountNumber = it.bank?.accountId!!, toType = it.bank?.type!!,
                        orders = map.value.toMutableSet(), toBranchCode = it.bank?.branchCode!!, toReference = "Payment from iZinga", fromReference = "Payment to ${it.name}",
                        emailAddress = it.emailAddress!!, emailNotify = "", emailSubject = "Payment from iZinga"
                    )
                }
            }
            ?.filterNotNull()?.toList()?.let {
                val bundle = payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.SHOP)?.apply { payouts = it } ?:
                PayoutBundle(payouts = it, createdBy = "System", type = PayoutType.SHOP)
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
                messengerRepo.findByIdOrNull(map.key)?.let { messng ->
                    bundle?.payouts?.firstOrNull { it.toId == messng.id }?.also { payout ->
                        payout.orders.addAll(map.value)
                        tips?.filter { messng.emailAddress == it.emailAddress }
                            ?.toMutableSet()
                            ?.also { payout.tips?.addAll(it) }
                    } ?: MessengerPayout(
                        toId = messng.id!!,
                        toName = messng.name!!,
                        toBankName = messng.bank?.name!!,
                        toType = messng.bank?.type!!,
                        toAccountNumber = messng.bank?.accountId!!,
                        orders = map.value.toMutableSet(),
                        toBranchCode = messng.bank!!.branchCode!!,
                        toReference = "Payment from iZinga", fromReference = "Payment to ${messng.name}",
                        tips = tips?.filter { messng.emailAddress == it.emailAddress }?.toMutableSet(),
                        emailAddress = messng.emailAddress!!, emailNotify = "", emailSubject = "Payment from iZinga"
                    ).also {
                        it.isPermEmployed = messng.isPermanentEmployed
                    }
                }
            }?.filterNotNull()
            ?.toList()
            ?.let {
                val bundle = bundle?.let { b -> b.payouts = it; b } ?: PayoutBundle(payouts = it, createdBy = "System", type = PayoutType.MESSENGER)
                payoutBundleRepo.save(bundle)
            }
    }

    override fun updatePayoutStatus(bundleResponse: PayoutBundleResults): PayoutBundle? {
        return payoutBundleRepo.findByIdOrNull(bundleResponse.bundleId)?.also { payoutBundle ->
            bundleResponse.payoutItemResults?.forEach { payResults ->
                payoutBundle.payouts.firstOrNull { payResults.payoutId == it.id }?.apply { paid = payResults.paid }
            } ?: payoutBundle.payouts.forEach { payout ->
                payout.paid = true
                orderRepo.findByIdIn(payout.orders.mapNotNull { it.id })
                    .forEach {
                        it.shopPaid = it.shopPaid || payoutBundle.type == PayoutType.SHOP
                        it.messengerPaid = it.messengerPaid || payoutBundle.type == PayoutType.MESSENGER
                        orderRepo.save(it)
                    }
            }
        }?.let {
            it.executed = true
            payoutBundleRepo.save(it)
        }
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