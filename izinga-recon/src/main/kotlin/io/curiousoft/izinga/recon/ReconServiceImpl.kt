package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.repo.OrderRepository
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.PayoutBundleRepo
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.*

@Service
class ReconServiceImpl(
    private val orderRepo: OrderRepository,
    private val payoutBundleRepo: PayoutBundleRepo,
    private val storeRepo: StoreRepository,
    private val messengerRepo: UserProfileRepo
) : ReconService {

    override fun generateNextPayoutsToShop(): PayoutBundle? {
        return payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.SHOP) ?: run {
            orderRepo.findByShopPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID)
        }?.groupBy { it.shopId }
            ?.map { map ->
                storeRepo.findByIdOrNull(map.key).let {
                    ShopPayout(
                    toId = map.key, toName = it!!.name!!, toBankName = it.bank?.name!!, toAccountNumber = it.bank?.accountId!!, toType = it.bank?.type!!,
                        orders = map.value, toBranchCode = it.bank!!.branchCode!!, toReference = "Payment from iZinga", fromReference = "Payment to ${it.name}",
                        emailAddress = it.emailAddress!!, emailNotify = "", emailSubject = "Payment from iZinga"
                    )
                }
            }
            ?.toList()?.let {
                val bundle = PayoutBundle(payouts = it, createdBy = "System", type = PayoutType.SHOP)
                payoutBundleRepo.save(bundle)
            }
    }

    override fun generateNextPayoutsToMessenger(): PayoutBundle? {
        return payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.MESSENGER) ?: run {
            orderRepo.findByMessengerPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID)
        }?.groupBy { it.shippingData?.messengerId }
            ?.map { map ->
                messengerRepo.findByIdOrNull(map.key).let { MessengerPayout(
                    toId = map.key!!, toName = it!!.name!!, toBankName = it.bank?.name!!, toType = it.bank?.type!!,
                    toAccountNumber = it.bank?.accountId!!, orders = map.value, toBranchCode = it.bank!!.branchCode!!, toReference = "Payment from iZinga", fromReference = "Payment to ${it.name}",
                    emailAddress = it.emailAddress!!, emailNotify = "", emailSubject = "Payment from iZinga"
                ) }
            }
            ?.toList()?.let {
                val bundle = PayoutBundle(payouts = it, createdBy = "System", type = PayoutType.MESSENGER)
                payoutBundleRepo.save(bundle)
            }
    }

    override fun updatePayoutStatus(bundleResponse: PayoutBundleResults): PayoutBundle? {
        return payoutBundleRepo.findByIdOrNull(bundleResponse.bundleId)?.also { payouts ->
            bundleResponse.payoutItemResults.forEach { payResults ->
                payouts.payouts.firstOrNull { payResults.payoutId == it.id }?.apply { paid = payResults.paid }
            }
        }?.let {
            it.executed = true
            payoutBundleRepo.save(it)
        }
    }

    override fun getAllPayouts(payoutType: PayoutType, fromDate: Date, toDate: Date): List<PayoutBundle> {
        return payoutBundleRepo.findByCreatedDateBeforeAndCreatedDateAfterAndType(fromDate, toDate, payoutType)
    }
}