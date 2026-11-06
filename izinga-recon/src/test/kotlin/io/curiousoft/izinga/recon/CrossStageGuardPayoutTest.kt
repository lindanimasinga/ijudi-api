package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Bank
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.ShippingData
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.notification.PayoutNotificationPort
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.MessengerPayout
import io.curiousoft.izinga.recon.payout.PayoutGenerationOutcome
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.*
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * ADR-028: Cross-stage idempotency guard tests.
 *
 * The guard [existsByToIdAndOrderId] must fire BEFORE the bank-details check.
 * When an order is already in an existing payout (any stage), the result must be
 * NotApplicable — not Deferred — and no repository save or notification may occur.
 *
 * Also covers DEF-1: a messenger with a valid bank but null emailAddress must still
 * produce a Created outcome and must not throw.
 */
class CrossStageGuardPayoutTest {

    private val storeRepo = mockk<StoreRepository>()
    private val userProfileRepo = mockk<UserProfileRepo>()
    private val shopPayoutRepository = mockk<ShopPayoutRepository>()
    private val messengerPayoutRepository = mockk<MessengerPayoutRepository>()
    private val ambassadorPayoutRepository = mockk<AmbassadorPayoutRepository>()
    private val referralPartnerPayoutRepository = mockk<ReferralPartnerPayoutRepository>()
    private val payoutBundleRepository = mockk<PayoutRepository>()
    private val foodCustomerCommissionRepo = mockk<FoodCustomerReferralCommissionRepo>()
    private val furnitureCustomerCommissionRepo = mockk<FurnitureCustomerReferralCommissionRepo>()
    private val storeStage1CommissionRepo = mockk<StorePartnerStage1CommissionRepo>()
    private val storeStage2CommissionRepo = mockk<StorePartnerStage2CommissionRepo>()
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>()
    private val payoutNotificationPort = mockk<PayoutNotificationPort>(relaxed = true)

    private lateinit var sut: ReconServiceImpl

    @Before
    fun setUp() {
        sut = ReconServiceImpl(
            storeRepo = storeRepo,
            userProfileRepo = userProfileRepo,
            shopPayoutRepo = shopPayoutRepository,
            messengerPayoutRepository = messengerPayoutRepository,
            ambassadorPayoutRepository = ambassadorPayoutRepository,
            referralPartnerPayoutRepository = referralPartnerPayoutRepository,
            payoutBundleRepository = payoutBundleRepository,
            foodCustomerCommissionRepo = foodCustomerCommissionRepo,
            furnitureCustomerCommissionRepo = furnitureCustomerCommissionRepo,
            storeStage1CommissionRepo = storeStage1CommissionRepo,
            storeStage2CommissionRepo = storeStage2CommissionRepo,
            applicationEventPublisher = applicationEventPublisher,
            ambassadorProperties = AmbassadorProperties(),
            payoutNotificationPort = payoutNotificationPort
        )
    }

    // ── Shop: cross-stage guard ───────────────────────────────────────────────

    /**
     * ADR-028-SHOP-GUARD-01: when existsByToIdAndOrderId returns true for a shop,
     * the result must be NotApplicable. shopPayoutRepo.save must never be called.
     * No notification is sent.
     */
    @Test
    fun `ADR-028 shop - existsByToIdAndOrderId true returns NotApplicable and skips save`() {
        val shopId = "shop-already-paid"
        val orderId = "order-already-in-payout"
        val store = makeStoreWithBank(shopId)
        val order = makeShopOrder(orderId, shopId)

        every { storeRepo.findById(shopId) } returns Optional.of(store)
        // Guard returns true: this order is already in an existing payout
        every { shopPayoutRepository.existsByToIdAndOrderId(shopId, orderId) } returns true

        val result = sut.generatePayoutForShopAndOrder(order)

        assertTrue("Expected NotApplicable when order already in existing payout",
            result is PayoutGenerationOutcome.NotApplicable)
        verify(exactly = 0) { shopPayoutRepository.save(any()) }
        verify(exactly = 0) { payoutNotificationPort.notifyStoreMissingBankDetails(any(), any(), any()) }
    }

    /**
     * ADR-028-SHOP-GUARD-02: guard runs BEFORE the bank check.
     * An order that is already in a payout for a store that now has no bank
     * must return NotApplicable, NOT Deferred — and no notification is sent.
     */
    @Test
    fun `ADR-028 shop - guard fires before bank check - already in payout plus no bank gives NotApplicable not Deferred`() {
        val shopId = "shop-no-bank-but-already-paid"
        val orderId = "order-already-in-payout-2"
        // Store has payment agreement but NO bank (would normally trigger Deferred)
        val store = makeStoreWithoutBank(shopId)
        val order = makeShopOrder(orderId, shopId)

        every { storeRepo.findById(shopId) } returns Optional.of(store)
        // Guard returns true BEFORE bank check is reached
        every { shopPayoutRepository.existsByToIdAndOrderId(shopId, orderId) } returns true

        val result = sut.generatePayoutForShopAndOrder(order)

        assertTrue("Expected NotApplicable (not Deferred) when order already in payout regardless of bank",
            result is PayoutGenerationOutcome.NotApplicable)
        verify(exactly = 0) { shopPayoutRepository.save(any()) }
        verify(exactly = 0) { payoutNotificationPort.notifyStoreMissingBankDetails(any(), any(), any()) }
    }

    // ── Messenger: cross-stage guard ─────────────────────────────────────────

    /**
     * ADR-028-MSG-GUARD-01: when existsByToIdAndOrderId returns true for a messenger,
     * the result must be NotApplicable. messengerPayoutRepository.save must never be called.
     * No notification is sent.
     */
    @Test
    fun `ADR-028 messenger - existsByToIdAndOrderId true returns NotApplicable and skips save`() {
        val messengerId = "messenger-already-paid"
        val orderId = "order-already-in-messenger-payout"
        val messenger = makeMessengerWithBank(messengerId)
        val order = makeMessengerOrder(orderId, messengerId)

        every { userProfileRepo.findById(messengerId) } returns Optional.of(messenger)
        // Guard returns true: this order is already in an existing messenger payout
        every { messengerPayoutRepository.existsByToIdAndOrderId(messengerId, orderId) } returns true

        val result = sut.generatePayoutForMessengerAndOrder(order)

        assertTrue("Expected NotApplicable when order already in existing messenger payout",
            result is PayoutGenerationOutcome.NotApplicable)
        verify(exactly = 0) { messengerPayoutRepository.save(any()) }
        verify(exactly = 0) { payoutNotificationPort.notifyDriverMissingBankDetails(any(), any(), any()) }
    }

    /**
     * ADR-028-MSG-GUARD-02: guard runs BEFORE the bank check for messenger.
     * An order already in a payout for a messenger who now has no bank
     * must return NotApplicable, NOT Deferred — and no notification is sent.
     */
    @Test
    fun `ADR-028 messenger - guard fires before bank check - already in payout plus no bank gives NotApplicable not Deferred`() {
        val messengerId = "messenger-no-bank-but-already-paid"
        val orderId = "order-already-in-messenger-payout-2"
        // Messenger has NO bank (would normally trigger Deferred + notification)
        val messenger = makeMessengerWithoutBank(messengerId)
        val order = makeMessengerOrder(orderId, messengerId)

        every { userProfileRepo.findById(messengerId) } returns Optional.of(messenger)
        // Guard returns true BEFORE bank check is reached
        every { messengerPayoutRepository.existsByToIdAndOrderId(messengerId, orderId) } returns true

        val result = sut.generatePayoutForMessengerAndOrder(order)

        assertTrue("Expected NotApplicable (not Deferred) when order already in payout regardless of bank",
            result is PayoutGenerationOutcome.NotApplicable)
        verify(exactly = 0) { messengerPayoutRepository.save(any()) }
        verify(exactly = 0) { payoutNotificationPort.notifyDriverMissingBankDetails(any(), any(), any()) }
    }

    // ── DEF-1: null emailAddress ──────────────────────────────────────────────

    /**
     * DEF-1: a messenger with a valid bank but null emailAddress must produce a Created
     * outcome. The payout's emailAddress must be null. No exception is thrown.
     */
    @Test
    fun `DEF-1 messenger with valid bank and null emailAddress returns Created with null payout emailAddress`() {
        val messengerId = "messenger-no-email"
        val orderId = "order-no-email"
        val messenger = makeMessengerWithBank(messengerId).also { it.emailAddress = null }
        val order = makeMessengerOrder(orderId, messengerId)

        val savedPayout = MessengerPayout(
            toId = messengerId, toName = "Test Messenger", toBankName = "FNB",
            toType = BankAccType.CHEQUE, toAccountNumber = "9876543210", toBranchCode = "250655",
            fromReference = "Payment to Test Messenger", toReference = "iZinga pay",
            emailNotify = "", emailAddress = null, emailSubject = "iZinga pay",
            orders = mutableSetOf(order)
        )

        every { userProfileRepo.findById(messengerId) } returns Optional.of(messenger)
        every { messengerPayoutRepository.existsByToIdAndOrderId(messengerId, orderId) } returns false
        every { messengerPayoutRepository.findByToIdAndPayoutStage(messengerId, PayoutStage.PENDING) } returns null
        every { messengerPayoutRepository.save(any()) } returns savedPayout

        val result = sut.generatePayoutForMessengerAndOrder(order)

        assertTrue("Expected Created outcome", result is PayoutGenerationOutcome.Created)
        val created = result as PayoutGenerationOutcome.Created<MessengerPayout>
        assertNull("payout.emailAddress must be null when messenger has no email", created.payout.emailAddress)
        verify(exactly = 1) { messengerPayoutRepository.save(any()) }
        verify(exactly = 0) { payoutNotificationPort.notifyDriverMissingBankDetails(any(), any(), any()) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeShopOrder(orderId: String, shopId: String): Order =
        Order().also {
            it.id = orderId
            it.shopId = shopId
            it.stage = OrderStage.STAGE_7_ALL_PAID
        }

    private fun makeMessengerOrder(orderId: String, messengerId: String): Order {
        val sd = ShippingData().apply {
            this.messengerId = messengerId
            deliveryFee = 50.0
        }
        return Order().also {
            it.id = orderId
            it.stage = OrderStage.STAGE_7_ALL_PAID
            it.shippingData = sd
        }
    }

    private fun makeStoreWithBank(id: String): StoreProfile {
        val bank = Bank().apply { name = "FNB"; accountId = "123456"; type = BankAccType.CHEQUE; branchCode = "250655" }
        val hours = makeBusinessHours()
        return StoreProfile(StoreType.FOOD, "Test Store", "teststore", "1 Store St", "img.jpg",
            "0811234567", mutableListOf("food"), hours, "owner-1", bank).also {
            it.id = id
            it.hasPaymentAgreement = true
            it.emailAddress = "store@test.com"
        }
    }

    private fun makeStoreWithoutBank(id: String): StoreProfile {
        val hours = makeBusinessHours()
        return StoreProfile(StoreType.FOOD, "Test Store", "teststore", "1 Store St", "img.jpg",
            "0811234567", mutableListOf("food"), hours, "owner-1", null).also {
            it.id = id
            it.hasPaymentAgreement = true
            it.emailAddress = "store@test.com"
        }
    }

    private fun makeMessengerWithBank(id: String): UserProfile {
        val bank = Bank().apply { name = "FNB"; accountId = "9876543210"; type = BankAccType.CHEQUE; branchCode = "250655" }
        return UserProfile("Test Messenger", UserProfile.SignUpReason.DELIVERY_DRIVER, "1 Driver St",
            "img.jpg", "0831111111", ProfileRoles.MESSENGER).also {
            it.id = id
            it.bank = bank
            it.emailAddress = "messenger@test.com"
        }
    }

    private fun makeMessengerWithoutBank(id: String): UserProfile =
        UserProfile("Test Messenger", UserProfile.SignUpReason.DELIVERY_DRIVER, "1 Driver St",
            "img.jpg", "0831111111", ProfileRoles.MESSENGER).also {
            it.id = id
            it.bank = null
            it.emailAddress = "messenger@test.com"
        }

    private fun makeBusinessHours(): ArrayList<io.curiousoft.izinga.commons.model.BusinessHours> {
        val hours = ArrayList<io.curiousoft.izinga.commons.model.BusinessHours>()
        val open = Date.from(LocalDateTime.now().withHour(8).toInstant(ZoneOffset.of("+02:00")))
        val close = Date.from(LocalDateTime.now().withHour(22).toInstant(ZoneOffset.of("+02:00")))
        DayOfWeek.values().forEach { day -> hours.add(io.curiousoft.izinga.commons.model.BusinessHours(day, open, close)) }
        return hours
    }
}
