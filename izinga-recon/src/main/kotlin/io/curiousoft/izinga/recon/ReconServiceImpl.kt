package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.Bank
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.notification.PayoutNotificationPort
import io.curiousoft.izinga.commons.payout.events.AmbassadorPayoutEvent
import io.curiousoft.izinga.commons.payout.events.OrderPayoutEvent
import io.curiousoft.izinga.commons.payout.events.ReferralPartnerPayoutEvent
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent
// ADR-026 Phase 5: AgentIdentityAuditService, CallerIdentityContext, CallerIdentityValidator,
// and AgentIdentityAuditLog removed — the get_payouts_for_user gate now lives in PayoutMcpController.
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.ReferralCommissionStatus
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.PayoutGenerationOutcome
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import org.slf4j.LoggerFactory
// ADR-026 Phase 5: Tool and ToolParam imports removed (get_payouts_for_user @Tool moved to PayoutMcpController)
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
    private val payoutBundleRepository: PayoutRepository,
    private val foodCustomerCommissionRepo: FoodCustomerReferralCommissionRepo,
    private val furnitureCustomerCommissionRepo: FurnitureCustomerReferralCommissionRepo,
    private val storeStage1CommissionRepo: StorePartnerStage1CommissionRepo,
    private val storeStage2CommissionRepo: StorePartnerStage2CommissionRepo,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val ambassadorProperties: AmbassadorProperties,
    /**
     * BUG-3/GAP-7: Optional notification port for alerting users about missing bank details.
     * Nullable to keep backward-compatibility with test setup that doesn't wire notifications.
     * Live implementation: WhatsappNotificationService (izinga-messaging), wired at ordermanager runtime.
     * Templates in use:
     *   - `driver_missing_bank_details` (BUG-3, driver path) — LIVE (Meta returned HTTP 200 in UAT)
     *   - `store_missing_bank_details` (GAP-7, store path) — pending Meta approval (returned HTTP 400)
     * Notifications are throttled per recipient (24-hour cooldown) inside the port implementation.
     */
    private val payoutNotificationPort: PayoutNotificationPort? = null,
) : ReconService {

    private val logger = LoggerFactory.getLogger(ReconServiceImpl::class.java)

    override fun generatePayoutForShopAndOrder(order: Order): PayoutGenerationOutcome<ShopPayout> {
        // ── By-design NotApplicable checks ───────────────────────────────────
        if (order.stage != OrderStage.STAGE_7_ALL_PAID) {
            logger.info("no payout generated for order {} — not at STAGE_7_ALL_PAID", order.id)
            return PayoutGenerationOutcome.NotApplicable("order ${order.id} not at STAGE_7_ALL_PAID")
        }

        val store = storeRepo.findByIdOrNull(order.shopId)
        if (store?.hasPaymentAgreement == false) {
            logger.info("no payout created — store {} has no payout agreement", store.id)
            return PayoutGenerationOutcome.NotApplicable("store ${store.id} has no payment agreement")
        }

        val storeId = store?.id
        if (storeId == null) {
            logger.warn("no payout created — store not found for shopId {}", order.shopId)
            return PayoutGenerationOutcome.NotApplicable("store not found for shopId ${order.shopId}")
        }

        // ── ADR-028 cross-stage idempotency guard ─────────────────────────────
        // Prevents double-pay across all PayoutStages (PENDING, PROCESSING, COMPLETED, VOIDED).
        // This is the primary guard; the in-bundle id check below is a second line of defence.
        val orderId = order.id
        if (orderId != null && shopPayoutRepo.existsByToIdAndOrderId(storeId, orderId)) {
            logger.info("order {} already included in an existing shop payout for store {}; skipping", orderId, storeId)
            return PayoutGenerationOutcome.NotApplicable("order already included in an existing shop payout")
        }

        // ── Deferred: missing bank details ────────────────────────────────────
        // GAP-7: notify store owner once per 24h (throttled inside the port implementation).
        // `store_missing_bank_details` template is pending Meta approval (returned HTTP 400 in UAT).
        val bank = store.bank
        if (bank == null) {
            val storeName = store.name ?: "Store"
            val mobile = store.mobileNumber
            logger.warn(
                "store {} has a payout agreement but no bank details on file; " +
                "deferring payout for order {}. Store must add bank details to receive payouts.",
                storeId, orderId
            )
            if (!mobile.isNullOrBlank()) {
                payoutNotificationPort?.notifyStoreMissingBankDetails(mobile, storeName, storeId)
            }
            return PayoutGenerationOutcome.Deferred("store $storeId missing bank details")
        }

        // ── Success path ──────────────────────────────────────────────────────
        val payout = shopPayoutRepo.findByToIdAndPayoutStage(order.shopId, PayoutStage.PENDING) ?: ShopPayout(
            toId = store.id!!,
            toName = store.name ?: "",
            toBankName = bank.name!!,
            toType = bank.type!!,
            toAccountNumber = getPayoutAccountNumber(bank.accountId, bank.type)!!,
            orders = mutableSetOf(),
            toBranchCode = bank.branchCode!!,
            fromReference = "Payment to ${store.name}",
            toReference = "iZinga pay",
            emailNotify = "",
            emailAddress = store.emailAddress,
            emailSubject = "iZinga pay",
        )

        // IDEMPOTENCY (second line of defence): Order.equals() is id-based but hashCode() is
        // identity-based, so MutableSet dedup is identity-based. Guard explicitly by id to
        // prevent duplicate order entries within the bundle (inflates total and causes overpayment).
        if (payout.orders.none { it.id == order.id }) {
            payout.orders.add(order)
            payout.emailSent = false
        }
        return PayoutGenerationOutcome.Created(shopPayoutRepo.save(payout))
    }

    override fun generatePayoutForMessengerAndOrder(order: Order): PayoutGenerationOutcome<MessengerPayout> {
        // ── By-design NotApplicable checks ───────────────────────────────────
        val messng = userProfileRepo.findByIdOrNull(order.shippingData?.messengerId!!)
        if (messng == null) {
            logger.warn("messenger not found for order {}; skipping payout", order.id)
            return PayoutGenerationOutcome.NotApplicable("messenger not found for order ${order.id}")
        }
        if (messng.isPermanentEmployed == true && order.tip?.let { it <= 0 } == true) {
            logger.info("perm-employed messenger {} with zero tip — not applicable", messng.id)
            return PayoutGenerationOutcome.NotApplicable("perm-employed messenger ${messng.id} with tip <= 0")
        }

        val messengerId = messng.id
        if (messengerId == null) {
            logger.warn("messenger has no id for order {}; skipping payout", order.id)
            return PayoutGenerationOutcome.NotApplicable("messenger has no id for order ${order.id}")
        }

        // ── ADR-028 cross-stage idempotency guard ─────────────────────────────
        val orderId = order.id
        if (orderId != null && messengerPayoutRepository.existsByToIdAndOrderId(messengerId, orderId)) {
            logger.info("order {} already included in an existing messenger payout for messenger {}; skipping", orderId, messengerId)
            return PayoutGenerationOutcome.NotApplicable("order already included in an existing messenger payout")
        }

        // ── Deferred: missing bank details ────────────────────────────────────
        // BUG-3: notify driver once per 24h (throttled inside the port implementation).
        // Template `driver_missing_bank_details` is LIVE (Meta returned HTTP 200 in UAT).
        val bank = messng.bank
        if (bank == null) {
            val messengerName = messng.name ?: "Driver"
            val mobile = messng.mobileNumber
            logger.warn(
                "messenger {} has no bank details on file; deferring payout for order {}. " +
                "Driver must add bank details at https://driver.izinga.co.za to receive payouts.",
                messengerId, orderId
            )
            if (!mobile.isNullOrBlank()) {
                payoutNotificationPort?.notifyDriverMissingBankDetails(mobile, messengerName, messengerId)
            }
            return PayoutGenerationOutcome.Deferred("messenger $messengerId missing bank details")
        }

        // ── Success path ──────────────────────────────────────────────────────
        val payout = messengerPayoutRepository.findByToIdAndPayoutStage(messengerId, PayoutStage.PENDING) ?: MessengerPayout(
            toId = messengerId,
            toName = messng.name ?: "",
            toBankName = bank.name!!,
            toType = bank.type!!,
            toAccountNumber = getPayoutAccountNumber(bank.accountId, bank.type)!!,
            orders = mutableSetOf(),
            toBranchCode = bank.branchCode!!,
            toReference = "iZinga pay",
            fromReference = "Payment to ${messng.name}",
            tips = mutableSetOf(),
            emailAddress = messng.emailAddress,   // DEF-1: null is acceptable; email sender skips blank/null
            emailNotify = "",
            emailSubject = "iZinga pay"
        ).also {
            it.isPermEmployed = messng.isPermanentEmployed ?: false
        }

        // IDEMPOTENCY (second line of defence): use explicit id comparison (not equals/hashCode)
        // to guard against duplicate order entries within the bundle.
        payout.orders.firstOrNull { it.id == order.id }
            ?.let { logger.info("payout already processed for the order {}", it.id) } ?: run {
                logger.info("adding order {} to messenger payout {}", order.id, payout.id)
                payout.orders.add(order)
                payout.emailSent = false
            }
        return PayoutGenerationOutcome.Created(messengerPayoutRepository.save(payout))
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

        val (commissionAmount, vehicleType) = resolveAmbassadorCommission(driver)

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
            commissionAmount = commissionAmount,
            triggerDriverId = driverId,
            triggerDriverVehicleType = vehicleType,
            payoutStage = PayoutStage.PENDING
        )

        val saved = ambassadorPayoutRepository.save(payout)
        logger.info("created ambassador payout {} for ambassador {} on driver approval {} vehicleType={}", saved.id, ambassadorId, driverId, vehicleType)

        applicationEventPublisher.publishEvent(
            AmbassadorPayoutEvent(
                source = this,
                ambassadorId = ambassadorId,
                driverId = driverId,
                commissionAmount = commissionAmount,
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
        // GAP-3: on paid=false, revert PROCESSING→PENDING so the payout is re-exported in the
        // next CSV cycle. Also persist the failure reason and increment the attempt counter.

        bundleResponse.payoutItemResults
            .filter { it.type == PayoutType.SHOP }
            .mapNotNull { result ->
                shopPayoutRepo.findByToIdAndPayoutStage(result.toId, PayoutStage.PROCESSING)
                    ?.let { payout ->
                        payout.paid = result.paid
                        payout.bundleId = bundleResponse.bundleId
                        payout.attemptCount++
                        if (result.paid) {
                            payout.payoutStage = PayoutStage.COMPLETED
                            payout.failureReason = null
                        } else {
                            payout.failureReason = result.message
                            payout.payoutStage = PayoutStage.PENDING
                            logger.warn(
                                "shop payout {} for {} reverted to PENDING after failed attempt {}; reason={}",
                                payout.id, result.toId, payout.attemptCount, result.message
                            )
                        }
                        shopPayoutRepo.save(payout)
                        payout
                    }
            }
            .filter { it.paid }
            .flatMap { it.orders }
            .forEach {
                applicationEventPublisher.publishEvent(
                    OrderPayoutEvent(source = this, orderId = it.id!!, isStorePaid = true)
                )
            }

        bundleResponse.payoutItemResults
            .filter { it.type == PayoutType.MESSENGER }
            .mapNotNull { result ->
                messengerPayoutRepository.findByToIdAndPayoutStage(result.toId, PayoutStage.PROCESSING)
                    ?.let { payout ->
                        payout.paid = result.paid
                        payout.attemptCount++
                        if (result.paid) {
                            payout.payoutStage = PayoutStage.COMPLETED
                            payout.failureReason = null
                        } else {
                            payout.failureReason = result.message
                            payout.payoutStage = PayoutStage.PENDING
                            logger.warn(
                                "messenger payout {} for {} reverted to PENDING after failed attempt {}; reason={}",
                                payout.id, result.toId, payout.attemptCount, result.message
                            )
                        }
                        messengerPayoutRepository.save(payout)
                        payout
                    }
            }
            .filter { it.paid }
            .flatMap { it.orders }
            .forEach {
                applicationEventPublisher.publishEvent(
                    OrderPayoutEvent(source = this, orderId = it.id!!, isMessengerPaid = true)
                )
            }

        bundleResponse.payoutItemResults
            .filter { it.type == PayoutType.AMBASSADOR }
            .flatMap { result ->
                ambassadorPayoutRepository.findAllByToIdAndPayoutStage(result.toId, PayoutStage.PROCESSING)
                    .onEach { payout ->
                        payout.paid = result.paid
                        payout.attemptCount++
                        if (result.paid) {
                            payout.payoutStage = PayoutStage.COMPLETED
                            payout.failureReason = null
                        } else {
                            payout.failureReason = result.message
                            payout.payoutStage = PayoutStage.PENDING
                            logger.warn(
                                "ambassador payout {} for {} reverted to PENDING after failed attempt {}; reason={}",
                                payout.id, result.toId, payout.attemptCount, result.message
                            )
                        }
                    }
                    .also { payouts -> ambassadorPayoutRepository.saveAll(payouts) }
            }

        // RP-009: handle referral partner payout completions and sync commission status
        bundleResponse.payoutItemResults
            .filter { it.type == PayoutType.REFERRAL_PARTNER }
            .flatMap { result ->
                referralPartnerPayoutRepository.findAllByToIdAndPayoutStage(result.toId, PayoutStage.PROCESSING)
                    .onEach { payout ->
                        payout.paid = result.paid
                        payout.attemptCount++
                        if (result.paid) {
                            payout.payoutStage = PayoutStage.COMPLETED
                            payout.failureReason = null
                        } else {
                            payout.failureReason = result.message
                            payout.payoutStage = PayoutStage.PENDING
                            logger.warn(
                                "referral partner payout {} for {} reverted to PENDING after failed attempt {}; reason={}",
                                payout.id, result.toId, payout.attemptCount, result.message
                            )
                        }
                    }
                    .also { payouts -> referralPartnerPayoutRepository.saveAll(payouts) }
            }
            .filter { it.paid }
            .onEach { payout ->
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
                ReferralCommissionType.FURNITURE_CUSTOMER_REFERRAL -> {
                    val commission = furnitureCustomerCommissionRepo.findByCustomerId(payout.triggerReferenceId)
                    if (commission != null) {
                        furnitureCustomerCommissionRepo.save(commission.copy(status = ReferralCommissionStatus.PAID))
                        logger.info("[rp-012] synced furniture customer commission to PAID for customerId={}", payout.triggerReferenceId)
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

    /**
     * ADR-026 Phase 5: @Tool and identity gate REMOVED from service.
     * The gate (CallerIdentityValidator.validateCallerOwnsId + audit) now lives in
     * PayoutMcpController, keeping service logic caller-agnostic (AC-04).
     * resolveCallerUserId() was also removed — replaced by McpCaller.resolveUserId()
     * in the controller, which performs the same prefix-loop lookup.
     *
     * <p>REST callers via ReconController remain unaffected — they call this method
     * directly on the ungated service (AC-04 compliance).</p>
     *
     * <p>MUST NEVER be annotated @Tool — see ReconService interface contract.</p>
     */
    override fun getAllPayouts(payoutType: PayoutType, from: Date, toDate: Date, toId: String): List<Payout> {
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

    override fun getCurrentPayoutBundleForAmbassadors(): PayoutBundle {
        return PayoutBundle(payouts = ambassadorPayoutRepository.findByPayoutStage(),
            type = PayoutType.AMBASSADOR,
            createdBy = "izinga-system")
    }

    override fun getCurrentPayoutBundleForReferralPartners(): PayoutBundle {
        return PayoutBundle(payouts = referralPartnerPayoutRepository.findByPayoutStage(),
            type = PayoutType.REFERRAL_PARTNER,
            createdBy = "izinga-system")
    }

    override fun findPayout(bundleId: String, payoutId: String): Payout? {
        val bundle = payoutBundleRepository.findByIdOrNull(bundleId)
        return when (bundle?.type) {
            PayoutType.SHOP -> shopPayoutRepo.findByIdOrNull(payoutId)
            PayoutType.MESSENGER -> messengerPayoutRepository.findByIdOrNull(payoutId)
            PayoutType.AMBASSADOR -> ambassadorPayoutRepository.findByIdOrNull(payoutId)
            PayoutType.REFERRAL_PARTNER -> referralPartnerPayoutRepository.findByIdOrNull(payoutId)
            null -> {
                logger.warn("[findPayout] bundle {} not found; falling back to cross-repo scan for payout {}", bundleId, payoutId)
                shopPayoutRepo.findByIdOrNull(payoutId)
                    ?: messengerPayoutRepository.findByIdOrNull(payoutId)
                    ?: ambassadorPayoutRepository.findByIdOrNull(payoutId)
                    ?: referralPartnerPayoutRepository.findByIdOrNull(payoutId)
            }
        }
    }

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

        // RP-012: furniture customer referral commissions
        furnitureCustomerCommissionRepo.findAll()
            .filter { it.status == ReferralCommissionStatus.PENDING }
            .forEach { commission ->
                val existing = referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(
                    ReferralCommissionType.FURNITURE_CUSTOMER_REFERRAL, commission.customerId
                )
                if (existing == null) {
                    // Use the persisted amount — do NOT recompute at reconciliation time.
                    // The amount was calculated and stored by StoreOrderEventHandler at trigger time.
                    val result = generatePayoutForReferralPartner(
                        partnerId = commission.referralPartnerId,
                        amount = commission.amount,
                        commissionType = ReferralCommissionType.FURNITURE_CUSTOMER_REFERRAL,
                        triggerReferenceId = commission.customerId
                    )
                    if (result != null) created++ else skipped++
                }
            }

        logger.info("[rp-009] reconciliation complete: created={} skipped={}", created, skipped)
    }

    /**
     * Resolves the ambassador commission tier for a driver based on the driver's `description` field,
     * which stores the vehicle category label (e.g. "Bike Delivery Driver", "Small/Medium Vehicle Driver",
     * "Bakkie Delivery Driver", "Truck Delivery Driver").
     *
     * Returns a Pair of (commissionAmount, vehicleTypeLabel). The label is stored as a snapshot on the
     * AmbassadorPayout at creation time and never recomputed after that point.
     *
     * This function never throws — any unrecognised or null description falls back to the default rate.
     */
    internal fun resolveAmbassadorCommission(driver: UserProfile): Pair<BigDecimal, String> {
        val description = driver.description
        return when {
            description == null || description.isBlank() -> {
                logger.warn("Ambassador commission: driver {} has no description, using fallback rate", driver.id)
                ambassadorProperties.commissionAmount to "UNKNOWN"
            }
            description.contains("BIKE", ignoreCase = true) ->
                ambassadorProperties.commissionAmountBike to "BIKE"
            description.contains("SMALL", ignoreCase = true) || description.contains("MEDIUM", ignoreCase = true) ->
                ambassadorProperties.commissionAmountCar to "CAR"
            description.contains("BAKKIE", ignoreCase = true) ->
                ambassadorProperties.commissionAmountBakkie to "BAKKIE"
            description.contains("TRUCK", ignoreCase = true) ->
                ambassadorProperties.commissionAmountTruck to "TRUCK"
            else -> {
                logger.warn("Ambassador commission: driver {} has unrecognised description '{}', using fallback rate", driver.id, description)
                ambassadorProperties.commissionAmount to "UNKNOWN"
            }
        }
    }

    private fun normalizeBankAccountId(accountId: String?): String? = accountId?.replace("+27", "0")

    private fun getPayoutAccountNumber(accountId: String?, bankType: BankAccType?): String? =
        if (bankType == BankAccType.EWALLET) normalizeBankAccountId(accountId) else accountId

}
