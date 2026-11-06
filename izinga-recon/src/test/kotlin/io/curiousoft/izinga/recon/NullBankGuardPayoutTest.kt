package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.*
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * ADR-028 (was BUG-3 + GAP-7): Verifies that null bank details on messenger/store payout paths
 * return Deferred (not null, not throw) and trigger the notification port exactly once per run.
 * When bank details are present the outcome is Created.
 */
class NullBankGuardPayoutTest {

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

    @BeforeEach
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

    // ── Messenger / driver path (BUG-3 → ADR-028) ───────────────────────────

    @Test
    fun `BUG-3 - messenger with null bank returns Deferred and does not throw NPE`() {
        val messengerId = "messenger-no-bank"
        val messenger = makeMessengerWithoutBank(messengerId)
        val order = makeOrderForMessenger(messengerId)

        every { userProfileRepo.findById(messengerId) } returns Optional.of(messenger)
        every { messengerPayoutRepository.existsByToIdAndOrderId(messengerId, order.id!!) } returns false

        val result = sut.generatePayoutForMessengerAndOrder(order)

        assertTrue(result is PayoutGenerationOutcome.Deferred, "Expected Deferred but got $result")
        verify(exactly = 0) { messengerPayoutRepository.save(any()) }
    }

    @Test
    fun `BUG-3 - messenger with null bank triggers PayoutNotificationPort with recipientId`() {
        val messengerId = "messenger-no-bank-2"
        val messenger = makeMessengerWithoutBank(messengerId)
        val order = makeOrderForMessenger(messengerId)

        every { userProfileRepo.findById(messengerId) } returns Optional.of(messenger)
        every { messengerPayoutRepository.existsByToIdAndOrderId(messengerId, order.id!!) } returns false

        sut.generatePayoutForMessengerAndOrder(order)

        verify(exactly = 1) {
            payoutNotificationPort.notifyDriverMissingBankDetails(
                eq("0831111111"),
                eq("Test Messenger"),
                eq(messengerId)
            )
        }
    }

    @Test
    fun `BUG-3 - messenger with null bank and null mobile does not call notification port`() {
        val messengerId = "messenger-no-bank-no-mobile"
        val messenger = makeMessengerWithoutBank(messengerId).also { it.mobileNumber = null }
        val order = makeOrderForMessenger(messengerId)

        every { userProfileRepo.findById(messengerId) } returns Optional.of(messenger)
        every { messengerPayoutRepository.existsByToIdAndOrderId(messengerId, order.id!!) } returns false

        val result = sut.generatePayoutForMessengerAndOrder(order)

        assertTrue(result is PayoutGenerationOutcome.Deferred, "Expected Deferred but got $result")
        verify(exactly = 0) { payoutNotificationPort.notifyDriverMissingBankDetails(any(), any(), any()) }
    }

    @Test
    fun `BUG-3 - messenger with bank present returns Created`() {
        val messengerId = "messenger-with-bank"
        val messenger = makeMessengerWithBank(messengerId)
        val order = makeOrderForMessenger(messengerId)
        val savedPayout = MessengerPayout(
            toId = messengerId, toName = "Test Messenger", toBankName = "FNB",
            toType = BankAccType.CHEQUE, toAccountNumber = "0831111111", toBranchCode = "250655",
            fromReference = "Payment to Test Messenger", toReference = "iZinga pay",
            emailNotify = "", emailAddress = "test@test.com", emailSubject = "iZinga pay",
            orders = mutableSetOf()
        )

        every { userProfileRepo.findById(messengerId) } returns Optional.of(messenger)
        every { messengerPayoutRepository.existsByToIdAndOrderId(messengerId, order.id!!) } returns false
        every { messengerPayoutRepository.findByToIdAndPayoutStage(messengerId, PayoutStage.PENDING) } returns null
        every { messengerPayoutRepository.save(any()) } returns savedPayout

        val result = sut.generatePayoutForMessengerAndOrder(order)

        assertTrue(result is PayoutGenerationOutcome.Created, "Expected Created but got $result")
        verify(exactly = 0) { payoutNotificationPort.notifyDriverMissingBankDetails(any(), any(), any()) }
        verify(exactly = 1) { messengerPayoutRepository.save(any()) }
    }

    // ── Store / shop path (GAP-7 → ADR-028) ─────────────────────────────────

    @Test
    fun `GAP-7 - store with payment agreement but null bank returns Deferred`() {
        val shopId = "shop-no-bank"
        val store = makeStoreWithPaymentAgreementWithoutBank(shopId)
        val order = makeOrderForShop(shopId)

        every { storeRepo.findById(shopId) } returns Optional.of(store)
        every { shopPayoutRepository.existsByToIdAndOrderId(shopId, order.id!!) } returns false

        val result = sut.generatePayoutForShopAndOrder(order)

        assertTrue(result is PayoutGenerationOutcome.Deferred, "Expected Deferred but got $result")
        verify(exactly = 0) { shopPayoutRepository.save(any()) }
    }

    @Test
    fun `GAP-7 - store with null bank triggers PayoutNotificationPort with recipientId`() {
        val shopId = "shop-no-bank-notify"
        val store = makeStoreWithPaymentAgreementWithoutBank(shopId)
        val order = makeOrderForShop(shopId)

        every { storeRepo.findById(shopId) } returns Optional.of(store)
        every { shopPayoutRepository.existsByToIdAndOrderId(shopId, order.id!!) } returns false

        sut.generatePayoutForShopAndOrder(order)

        verify(exactly = 1) {
            payoutNotificationPort.notifyStoreMissingBankDetails(
                eq("0811234567"),
                eq("Test Store"),
                eq(shopId)
            )
        }
    }

    @Test
    fun `GAP-7 - store with null bank and null mobile does not call notification port`() {
        val shopId = "shop-no-bank-no-mobile"
        val store = makeStoreWithPaymentAgreementWithoutBank(shopId).also { it.mobileNumber = null }
        val order = makeOrderForShop(shopId)

        every { storeRepo.findById(shopId) } returns Optional.of(store)
        every { shopPayoutRepository.existsByToIdAndOrderId(shopId, order.id!!) } returns false

        val result = sut.generatePayoutForShopAndOrder(order)

        assertTrue(result is PayoutGenerationOutcome.Deferred, "Expected Deferred but got $result")
        verify(exactly = 0) { payoutNotificationPort.notifyStoreMissingBankDetails(any(), any(), any()) }
    }

    @Test
    fun `GAP-7 - store with payment agreement and bank present returns Created`() {
        val shopId = "shop-with-bank"
        val store = makeStoreWithPaymentAgreementAndBank(shopId)
        val order = makeOrderForShop(shopId)
        val savedPayout = io.curiousoft.izinga.recon.payout.ShopPayout(
            toId = shopId, toName = "Test Store", toBankName = "FNB",
            toType = BankAccType.CHEQUE, toAccountNumber = "123456", toBranchCode = "250655",
            fromReference = "Payment to Test Store", toReference = "iZinga pay",
            emailNotify = "", emailAddress = "shop@test.com", emailSubject = "iZinga pay",
            orders = mutableSetOf()
        )

        every { storeRepo.findById(shopId) } returns Optional.of(store)
        every { shopPayoutRepository.existsByToIdAndOrderId(shopId, order.id!!) } returns false
        every { shopPayoutRepository.findByToIdAndPayoutStage(shopId, PayoutStage.PENDING) } returns null
        every { shopPayoutRepository.save(any()) } returns savedPayout

        val result = sut.generatePayoutForShopAndOrder(order)

        assertTrue(result is PayoutGenerationOutcome.Created, "Expected Created but got $result")
    }

    @Test
    fun `GAP-7 - store without payment agreement returns NotApplicable regardless of bank`() {
        val shopId = "shop-no-agreement"
        val store = makeStoreWithPaymentAgreementAndBank(shopId).also { it.hasPaymentAgreement = false }
        val order = makeOrderForShop(shopId)

        every { storeRepo.findById(shopId) } returns Optional.of(store)

        val result = sut.generatePayoutForShopAndOrder(order)

        assertTrue(result is PayoutGenerationOutcome.NotApplicable, "Expected NotApplicable but got $result")
        verify(exactly = 0) { shopPayoutRepository.save(any()) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeMessengerWithoutBank(id: String): UserProfile {
        return UserProfile(
            "Test Messenger",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "1 Driver Street",
            "img.jpg",
            "0831111111",
            ProfileRoles.MESSENGER
        ).also {
            it.id = id
            it.bank = null
            it.emailAddress = "messenger@test.com"
        }
    }

    private fun makeMessengerWithBank(id: String): UserProfile {
        val bank = Bank().apply {
            name = "FNB"; accountId = "0831111111"; type = BankAccType.CHEQUE; branchCode = "250655"
        }
        return UserProfile(
            "Test Messenger",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "1 Driver Street",
            "img.jpg",
            "0831111111",
            ProfileRoles.MESSENGER
        ).also {
            it.id = id
            it.bank = bank
            it.emailAddress = "messenger@test.com"
        }
    }

    private fun makeOrderForMessenger(messengerId: String): Order {
        val shippingData = ShippingData().apply {
            this.messengerId = messengerId
            deliveryFee = 50.0
        }
        return Order().also {
            it.id = "order-${messengerId}"
            it.stage = OrderStage.STAGE_7_ALL_PAID
            it.shippingData = shippingData
        }
    }

    private fun makeStoreWithPaymentAgreementWithoutBank(id: String): StoreProfile {
        val hours = makeBusinessHours()
        return StoreProfile(StoreType.FOOD, "Test Store", "teststore", "1 Store St", "img.jpg",
            "0811234567", mutableListOf("food"), hours, "owner-1", null).also {
            it.id = id
            it.hasPaymentAgreement = true
            it.emailAddress = "store@test.com"
        }
    }

    private fun makeStoreWithPaymentAgreementAndBank(id: String): StoreProfile {
        val bank = Bank().apply {
            name = "FNB"; accountId = "123456"; type = BankAccType.CHEQUE; branchCode = "250655"
        }
        val hours = makeBusinessHours()
        return StoreProfile(StoreType.FOOD, "Test Store", "teststore", "1 Store St", "img.jpg",
            "0811234567", mutableListOf("food"), hours, "owner-1", bank).also {
            it.id = id
            it.hasPaymentAgreement = true
            it.emailAddress = "store@test.com"
        }
    }

    private fun makeOrderForShop(shopId: String): Order {
        return Order().also {
            it.id = "order-${shopId}"
            it.shopId = shopId
            it.stage = OrderStage.STAGE_7_ALL_PAID
        }
    }

    private fun makeBusinessHours(): ArrayList<BusinessHours> {
        val hours = ArrayList<BusinessHours>()
        val open = Date.from(LocalDateTime.now().withHour(8).toInstant(ZoneOffset.of("+02:00")))
        val close = Date.from(LocalDateTime.now().withHour(22).toInstant(ZoneOffset.of("+02:00")))
        DayOfWeek.values().forEach { day -> hours.add(BusinessHours(day, open, close)) }
        return hours
    }
}
