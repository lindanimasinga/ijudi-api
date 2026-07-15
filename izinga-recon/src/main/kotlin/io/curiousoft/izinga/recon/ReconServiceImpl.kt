package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.Bank
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.payout.events.AmbassadorPayoutEvent
import io.curiousoft.izinga.commons.payout.events.OrderPayoutEvent
import io.curiousoft.izinga.commons.payout.events.ReferralPartnerPayoutEvent
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.ReferralCommissionStatus
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

@Service
class ReconServiceImpl(
    private val storeRepo: StoreRepository,
    private val userProfileRepo: UserProfileRepo,
    private val shopPayoutRepo: ShopPayoutRepository,
    private val messengerPayoutRepository: MessengerPayoutRepository,
    private val ambassadorPayoutRepository: AmbassadorPayoutRepository,
    private val referralPartnerPayoutRepository: ReferralPartnerPayoutRepository,
    private val foodCustomerCommissionRepo: FoodCustomerReferralCommissionRepo,
    private val storeStage1CommissionRepo: StorePartnerStage1CommissionRepo,
    private val storeStage2CommissionRepo: StorePartnerStage2CommissionRepo,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val ambassadorProperties: AmbassadorProperties
) : ReconService {

    private val logger = LoggerFactory.getLogger(ReconServiceImpl::class.java)

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
            toAccountNumber = getPayoutAccountNumber(store.bank?.accountId, store.bank?.type)!!,
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
        val messng = userProfileRepo.findByIdOrNull(order.shippingData?.messengerId!!)
        if (messng?.isPermanentEmployed == true && order.tip?.let { it <= 0 } == true) {
            return null
        }

        //val tips = tipsService.getTodayTips()
        val payout = messengerPayoutRepository.findByToIdAndPayoutStage(messng?.id!!, PayoutStage.PENDING) ?: MessengerPayout(
            toId = messng.id!!,
            toName = messng.name!!,
            toBankName = messng.bank?.name!!,
            toType = messng.bank?.type!!,
            toAccountNumber = getPayoutAccountNumber(messng.bank?.accountId, messng.bank?.type)!!,
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

        payout.orders.firstOrNull { it == order }
            ?.let { logger.info("payout already processed for the order {}", it.id) } ?: run {
                logger.info("adding order {} to messenger payout {}", order.id, payout.id)
                payout.orders.add(order)
                payout.emailSent = false
            }
        return messengerPayoutRepository.save(payout)
    }

    override fun generatePayoutForAmbassadorAndApproval(driver: UserProfile, ambassador: UserProfile): AmbassadorPayout? {
        val ambassadorId = ambassador.id ?: run {
            logger.warn("ambassador has no id, skipping payout generation")
            return null
        }
        val driverId = driver.id ?: run {
            logger.warn("driver has no id, skipping ambassador payout")
            return null
        }

        val existing = ambassadorPayoutRepository.findByTriggerDriverId(driverId)
        if (existing != null) {
            logger.warn(
                "driver {} already has an ambassador commission payout {}; skipping duplicate",
                driverId, existing.id
            )
            return null
        }

        val payout = AmbassadorPayout(
            toId = ambassadorId,
            toName = ambassador.name ?: "",
            toBankName = ambassador.bank?.name ?: "",
            toType = ambassador.bank?.type ?: BankAccType.CHEQUE,
            toAccountNumber = getPayoutAccountNumber(ambassador.bank?.accountId, ambassador.bank?.type) ?: "",
            toBranchCode = ambassador.bank?.branchCode ?: "",
            fromReference = "Ambassador commission for ${ambassador.name}",
            toReference = "iZinga pay",
            emailNotify = "",
            emailAddress = ambassador.emailAddress,
            emailSubject = "iZinga Ambassador Commission",
            orders = mutableSetOf(),
            commissionAmount = ambassadorProperties.commissionAmount,
            triggerDriverId = driverId,
            payoutStage = PayoutStage.PENDING
        )

        val saved = ambassadorPayoutRepository.save(payout)
        logger.info("created ambassador payout {} for ambassador {} on driver approval {}", saved.id, ambassadorId, driverId)

        applicationEventPublisher.publishEvent(
            AmbassadorPayoutEvent(
                source = this,
                ambassadorId = ambassadorId,
                driverId = driverId,
                commissionAmount = ambassadorProperties.commissionAmount,
                payoutId = saved.id!!
            )
        )

        return saved
    }

    @Async
    @EventListener
    override fun handleProfileUpdated(event: ProfileUpdatedEvent) {
        when (val profile = event.profile) {
            is UserProfile -> refreshPendingPayoutBankDetails(
                payouts = messengerPayoutRepository.findAllByToIdAndPayoutStage(profile.id ?: return, PayoutStage.PENDING),
                bank = profile.bank
            )
            is StoreProfile -> refreshPendingPayoutBankDetails(
                payouts = shopPayoutRepo.findAllByToIdAndPayoutStage(profile.id ?: return, PayoutStage.PENDING),
                bank = profile.bank
            )
        }
    }

    private fun refreshPendingPayoutBankDetails(payouts: List<Payout>, bank: Bank?) {
        if (payouts.isEmpty()) {
            return
        }

        bank ?: return
        val bankName = bank.name ?: return
        val bankType = bank.type ?: return
        val branchCode = bank.branchCode ?: return
        val accountId = if (bankType == BankAccType.EWALLET) {
            normalizeBankAccountId(bank.accountId) ?: return
        } else null

        payouts.forEach {
            it.toBankName = bankName
            it.toType = bankType
            if (accountId != null) {
                it.toAccountNumber = accountId
            }
            it.toBranchCode = branchCode
        }
        val shopPayouts = payouts.filterIsInstance<ShopPayout>()
        if (shopPayouts.isNotEmpty()) {
            shopPayoutRepo.saveAll(shopPayouts)
        }

        val messengerPayouts = payouts.filterIsInstance<MessengerPayout>()
        if (messengerPayouts.isNotEmpty()) {
            messengerPayoutRepository.saveAll(messengerPayouts)
        }
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

        bundleResponse.payoutItemResults
            .filter { it.type == PayoutType.AMBASSADOR }
            .flatMap { result ->
                ambassadorPayoutRepository.findAllByToIdAndPayoutStage(result.toId, PayoutStage.PROCESSING)
                    .onEach { payout -> payout.paid = result.paid }
                    .also { payouts -> ambassadorPayoutRepository.saveAll(payouts) }
            }
            .filter { it.paid }
            .onEach {
                it.payoutStage = PayoutStage.COMPLETED
                ambassadorPayoutRepository.save(it)
            }

        // RP-009: handle referral partner payout completions and sync commission status
        bundleResponse.payoutItemResults
            .filter { it.type == PayoutType.REFERRAL_PARTNER }
            .flatMap { result ->
                referralPartnerPayoutRepository.findAllByToIdAndPayoutStage(result.toId, PayoutStage.PROCESSING)
                    .onEach { payout -> payout.paid = result.paid }
                    .also { payouts -> referralPartnerPayoutRepository.saveAll(payouts) }
            }
            .filter { it.paid }
            .onEach { payout ->
                payout.payoutStage = PayoutStage.COMPLETED
                referralPartnerPayoutRepository.save(payout)
                syncCommissionStatusToPaid(payout)
            }
    }

    /**
     * RP-009: When a ReferralPartnerPayout is COMPLETED, update the corresponding commission
     * record's status to PAID so the audit ledger reflects the settled state.
     */
    private fun syncCommissionStatusToPaid(payout: ReferralPartnerPayout) {
        try {
            when (payout.commissionType) {
                ReferralCommissionType.FOOD_CUSTOMER_REFERRAL -> {
                    val commission = foodCustomerCommissionRepo.findByCustomerId(payout.triggerReferenceId)
                    if (commission != null) {
                        foodCustomerCommissionRepo.save(commission.copy(status = ReferralCommissionStatus.PAID))
                        logger.info("[rp-009] synced food customer commission to PAID for customerId={}", payout.triggerReferenceId)
                    }
                }
                ReferralCommissionType.STORE_PARTNER_STAGE_1 -> {
                    val commission = storeStage1CommissionRepo.findByStoreId(payout.triggerReferenceId)
                    if (commission != null) {
                        storeStage1CommissionRepo.save(commission.copy(status = ReferralCommissionStatus.PAID))
                        logger.info("[rp-009] synced stage1 commission to PAID for storeId={}", payout.triggerReferenceId)
                    }
                }
                ReferralCommissionType.STORE_PARTNER_STAGE_2 -> {
                    val commission = storeStage2CommissionRepo.findByStoreId(payout.triggerReferenceId)
                    if (commission != null) {
                        storeStage2CommissionRepo.save(commission.copy(status = ReferralCommissionStatus.PAID))
                        logger.info("[rp-009] synced stage2 commission to PAID for storeId={}", payout.triggerReferenceId)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[rp-009] failed to sync commission status to PAID for payout {}: {}", payout.id, e.message)
        }
    }

    override fun updateBundle(bundle: PayoutBundle) {
        return bundle.payouts.groupBy { it.javaClass.name }
            .forEach( {
                when (it.key) {
                    ShopPayout::class.qualifiedName -> shopPayoutRepo.saveAll(it.value as List<ShopPayout>)
                    MessengerPayout::class.qualifiedName -> messengerPayoutRepository.saveAll(it.value as List<MessengerPayout>)
                    AmbassadorPayout::class.qualifiedName -> ambassadorPayoutRepository.saveAll(it.value as List<AmbassadorPayout>)
                    ReferralPartnerPayout::class.qualifiedName -> referralPartnerPayoutRepository.saveAll(it.value as List<ReferralPartnerPayout>)
                }
        })
    }

    @Tool(name="get_payouts_for_user",  description = "Get all payouts for a given payout type, date range and toId. The default date range is past 7 days. " +
            "The Payout will have all orders for this payout and the total amount for the payout. " +
            "For driver the payoutType is MESSENGER and the toId is the driver id. " +
            "For shop the payoutType is SHOP and the toId is the shop id.")
    override fun getAllPayouts(@ToolParam(description = "For driver the payoutType is MESSENGER") payoutType: PayoutType,
                               @ToolParam(description = "Date time format is like 2026-04-09T00:00:00.000+00:00") from: Date,
                               @ToolParam(description = "Date time format is like 2026-04-09T00:00:00.000+00:00") toDate: Date,
                               @ToolParam(description = "This is the Driver/Messenger id to use for lookup") toId: String): List<Payout> {
        return when (payoutType) {
            PayoutType.SHOP -> shopPayoutRepo.findByModifiedDateBetweenAndToId(from, toDate, toId)
            PayoutType.MESSENGER -> messengerPayoutRepository.findByModifiedDateBetweenAndToId(from, toDate, toId)
            PayoutType.AMBASSADOR -> ambassadorPayoutRepository.findByModifiedDateBetweenAndToId(from, toDate, toId)
            PayoutType.REFERRAL_PARTNER -> referralPartnerPayoutRepository.findByModifiedDateBetweenAndToId(from, toDate, toId)
        }
    }

    override fun getAllPayoutsForMessengerAdmin(from: Date, toDate: Date, messengerAdminId: String, messengerId: String?): List<Payout> {
        val messengerAdmin = userProfileRepo.findByIdOrNull(messengerAdminId)
            ?: throw IllegalArgumentException("Messenger admin not found")

        if (messengerAdmin.role != ProfileRoles.MESSENGER_ADMIN) {
            throw IllegalArgumentException("Profile is not a messenger admin")
        }

        val managedMessengerIds = userProfileRepo.findByRoleAndMessengerAdminId(ProfileRoles.MESSENGER, messengerAdminId)
            .mapNotNull { it.id }

        if (managedMessengerIds.isEmpty()) {
            return emptyList()
        }

        val filteredMessengerIds = if (!messengerId.isNullOrBlank()) {
            if (!managedMessengerIds.contains(messengerId)) {
                throw IllegalArgumentException("Messenger does not belong to this admin")
            }
            listOf(messengerId)
        } else managedMessengerIds

        return messengerPayoutRepository.findByModifiedDateBetweenAndToIdIn(from, toDate, filteredMessengerIds)
    }

    override fun getAllPayoutBundles(payoutType: PayoutType, from: Date, toDate: Date): List<Payout> {
        return when (payoutType) {
            PayoutType.SHOP -> shopPayoutRepo.findByModifiedDateBetween(from, toDate)
            PayoutType.MESSENGER -> messengerPayoutRepository.findByModifiedDateBetween(from, toDate)
            PayoutType.AMBASSADOR -> ambassadorPayoutRepository.findByModifiedDateBetween(from, toDate)
            PayoutType.REFERRAL_PARTNER -> referralPartnerPayoutRepository.findByModifiedDateBetween(from, toDate)
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

    // RP-009 ─────────────────────────────────────────────────────────────────

    override fun generatePayoutForReferralPartner(
        partnerId: String,
        amount: BigDecimal,
        commissionType: ReferralCommissionType,
        triggerReferenceId: String
    ): ReferralPartnerPayout? {
        // Dedup: the compound index (commissionType, triggerReferenceId) is the authoritative guard,
        // but we also check in-application to avoid the DuplicateKeyException round-trip on the hot path.
        val existing = referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(commissionType, triggerReferenceId)
        if (existing != null) {
            logger.warn(
                "[rp-009] payout already exists for commissionType={} triggerReferenceId={}; skipping duplicate",
                commissionType, triggerReferenceId
            )
            return null
        }

        val partner = userProfileRepo.findByIdOrNull(partnerId)
        if (partner == null) {
            logger.warn("[rp-009] referral partner {} not found, skipping payout", partnerId)
            return null
        }

        val bank = partner.bank
        if (bank == null) {
            logger.warn(
                "[rp-009] referral partner {} has no bank details; skipping payout for commissionType={} triggerReferenceId={}. " +
                "Reconciliation pass will retry once bank details are set.",
                partnerId, commissionType, triggerReferenceId
            )
            return null
        }

        val payout = ReferralPartnerPayout(
            toId = partnerId,
            toName = partner.name ?: "",
            toBankName = bank.name ?: "",
            toType = bank.type ?: BankAccType.CHEQUE,
            toAccountNumber = getPayoutAccountNumber(bank.accountId, bank.type) ?: "",
            toBranchCode = bank.branchCode ?: "",
            fromReference = "Referral commission for ${partner.name}",
            toReference = "iZinga pay",
            emailNotify = "",
            emailAddress = partner.emailAddress,
            emailSubject = "iZinga Referral Partner Commission",
            commissionAmount = amount,
            commissionType = commissionType,
            triggerReferenceId = triggerReferenceId,
            payoutStage = PayoutStage.PENDING
        )

        return try {
            val saved = referralPartnerPayoutRepository.save(payout)
            logger.info(
                "[rp-009] created referral partner payout {} for partner={} commissionType={} triggerReferenceId={}",
                saved.id, partnerId, commissionType, triggerReferenceId
            )
            applicationEventPublisher.publishEvent(
                ReferralPartnerPayoutEvent(
                    source = this,
                    partnerId = partnerId,
                    commissionType = commissionType,
                    triggerReferenceId = triggerReferenceId,
                    commissionAmount = amount,
                    payoutId = saved.id!!
                )
            )
            saved
        } catch (e: DuplicateKeyException) {
            logger.warn(
                "[rp-009] duplicate key on save for commissionType={} triggerReferenceId={}; concurrent insert detected, skipping",
                commissionType, triggerReferenceId
            )
            null
        }
    }

    /**
     * RP-009 fallback reconciliation: runs daily (and on-demand) to pick up any PENDING commission
     * records that were never connected to a payout — including backfill for records created by PR #114.
     * Partners without bank details are skipped; they will be retried on the next run.
     */
    @Scheduled(cron = "0 30 2 * * *") // 02:30 daily — runs after the nightly payout bundle
    override fun reconcilePendingReferralCommissions() {
        logger.info("[rp-009] starting referral commission reconciliation pass")
        var created = 0
        var skipped = 0

        // RP-006: food customer referral commissions
        foodCustomerCommissionRepo.findAll()
            .filter { it.status == ReferralCommissionStatus.PENDING }
            .forEach { commission ->
                val existing = referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(
                    ReferralCommissionType.FOOD_CUSTOMER_REFERRAL, commission.customerId
                )
                if (existing == null) {
                    val result = generatePayoutForReferralPartner(
                        partnerId = commission.referralPartnerId,
                        amount = commission.amount,
                        commissionType = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL,
                        triggerReferenceId = commission.customerId
                    )
                    if (result != null) created++ else skipped++
                }
            }

        // RP-007: store partner stage 1 commissions
        storeStage1CommissionRepo.findAll()
            .filter { it.status == ReferralCommissionStatus.PENDING }
            .forEach { commission ->
                val existing = referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(
                    ReferralCommissionType.STORE_PARTNER_STAGE_1, commission.storeId
                )
                if (existing == null) {
                    val result = generatePayoutForReferralPartner(
                        partnerId = commission.referralPartnerId,
                        amount = commission.amount,
                        commissionType = ReferralCommissionType.STORE_PARTNER_STAGE_1,
                        triggerReferenceId = commission.storeId
                    )
                    if (result != null) created++ else skipped++
                }
            }

        // RP-008: store partner stage 2 commissions
        storeStage2CommissionRepo.findAll()
            .filter { it.status == ReferralCommissionStatus.PENDING }
            .forEach { commission ->
                val existing = referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(
                    ReferralCommissionType.STORE_PARTNER_STAGE_2, commission.storeId
                )
                if (existing == null) {
                    val result = generatePayoutForReferralPartner(
                        partnerId = commission.referralPartnerId,
                        amount = commission.amount,
                        commissionType = ReferralCommissionType.STORE_PARTNER_STAGE_2,
                        triggerReferenceId = commission.storeId
                    )
                    if (result != null) created++ else skipped++
                }
            }

        logger.info("[rp-009] reconciliation complete: created={} skipped={}", created, skipped)
    }

    private fun normalizeBankAccountId(accountId: String?): String? = accountId?.replace("+27", "0")

    private fun getPayoutAccountNumber(accountId: String?, bankType: BankAccType?): String? =
        if (bankType == BankAccType.EWALLET) normalizeBankAccountId(accountId) else accountId

}
