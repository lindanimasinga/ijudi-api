package io.curiousoft.izinga.payfast.service

import io.curiousoft.izinga.commons.model.SubscriptionTier
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.payfast.config.PayFastProperties
import io.curiousoft.izinga.payfast.model.MerchantSubscription
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionPricing
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionStatus
import io.curiousoft.izinga.payfast.repo.MerchantSubscriptionRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.*

/**
 * TIER-BILLING-01 / T-08: Unit tests for [PayFastCheckoutService].
 *
 * Covers:
 * - FREE tier rejection (AC-04)
 * - ICA gate (AC-05)
 * - Duplicate ACTIVE guard (AC-06)
 * - 30-minute idempotency (AC-07)
 * - PREMIUM_1 and PREMIUM_2 amount correctness (AC-08)
 * - Signature present in returned params
 * - Store not found
 */
class PayFastCheckoutServiceTest {

    private val storeRepository: StoreRepository = mockk()
    private val userProfileRepo: UserProfileRepo = mockk()
    private val subscriptionRepository: MerchantSubscriptionRepository = mockk()

    private val properties = PayFastProperties(
        merchantId = "16791971",
        merchantKey = "test-merchant-key",
        passphrase = "test-passphrase",
        baseUrl = "https://sandbox.payfast.co.za/eng/process",
        returnUrl = "https://biz.izinga.co.za/business/subscription-success",
        cancelUrl = "https://biz.izinga.co.za/business/subscription-cancel",
        notifyUrl = "https://api.izinga.co.za/merchant/subscription/itn",
        validateUrl = "https://sandbox.payfast.co.za/eng/query/validate"
    )
    private val signatureUtil = PayFastSignatureUtil(properties.passphrase)

    private lateinit var service: PayFastCheckoutService

    @BeforeEach
    fun setUp() {
        service = PayFastCheckoutService(
            properties, signatureUtil, subscriptionRepository, storeRepository, userProfileRepo
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // AC-04: FREE tier rejected
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout throws 400 for FREE tier`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.initiateCheckout("store-1", "owner-1", SubscriptionTier.FREE)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(exactly = 0) { storeRepository.findById(any()) }
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // AC-05: ICA gate
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout throws 422 MERCHANT_ICA_NOT_ACCEPTED when icaAccepted is null`() {
        val store = storeProfile(icaAccepted = null)
        every { storeRepository.findById("store-1") } returns Optional.of(store)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.statusCode)
        assertTrue(ex.reason?.contains("MERCHANT_ICA_NOT_ACCEPTED") == true)
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    @Test
    fun `initiateCheckout throws 422 when icaAccepted is false`() {
        val store = storeProfile(icaAccepted = false)
        every { storeRepository.findById("store-1") } returns Optional.of(store)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.statusCode)
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Store not found
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout throws 404 when store does not exist`() {
        every { storeRepository.findById("store-missing") } returns Optional.empty()

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.initiateCheckout("store-missing", "owner-1", SubscriptionTier.PREMIUM_1)
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // AC-06: Duplicate ACTIVE guard
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout throws 409 SUBSCRIPTION_ALREADY_ACTIVE when ACTIVE subscription exists`() {
        val store = storeProfile(icaAccepted = true)
        every { storeRepository.findById("store-1") } returns Optional.of(store)
        every {
            subscriptionRepository.findByStoreIdAndStatus("store-1", MerchantSubscriptionStatus.ACTIVE)
        } returns listOf(activeSubscription("store-1"))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertTrue(ex.reason?.contains("SUBSCRIPTION_ALREADY_ACTIVE") == true)
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // AC-07: 30-minute idempotency
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout returns existing mPaymentId when PENDING_PAYMENT within 30 minutes`() {
        val store = storeProfile(icaAccepted = true)
        val recent = pendingSubscription("store-1", createdMinutesAgo = 10L)
        every { storeRepository.findById("store-1") } returns Optional.of(store)
        every {
            subscriptionRepository.findByStoreIdAndStatus("store-1", MerchantSubscriptionStatus.ACTIVE)
        } returns emptyList()
        every {
            subscriptionRepository.findByStoreIdAndStatus("store-1", MerchantSubscriptionStatus.PENDING_PAYMENT)
        } returns listOf(recent)
        every { userProfileRepo.findById(any()) } returns Optional.empty()

        val result = service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)

        // Must return the existing mPaymentId
        assertEquals(recent.id, result["m_payment_id"])
        // Must NOT create a new record
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    @Test
    fun `initiateCheckout creates new subscription when PENDING_PAYMENT is older than 30 minutes`() {
        val store = storeProfile(icaAccepted = true)
        val stale = pendingSubscription("store-1", createdMinutesAgo = 31L)
        every { storeRepository.findById("store-1") } returns Optional.of(store)
        every {
            subscriptionRepository.findByStoreIdAndStatus("store-1", MerchantSubscriptionStatus.ACTIVE)
        } returns emptyList()
        every {
            subscriptionRepository.findByStoreIdAndStatus("store-1", MerchantSubscriptionStatus.PENDING_PAYMENT)
        } returns listOf(stale)
        every { subscriptionRepository.save(any()) } answers { firstArg() }
        every { userProfileRepo.findById(any()) } returns Optional.empty()

        val result = service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)

        // Must NOT use the stale record's mPaymentId
        assertNotEquals(stale.id, result["m_payment_id"])
        // Must save a new subscription
        verify(exactly = 1) { subscriptionRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // AC-08: Amount correctness
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout sets PREMIUM_1 amount to 800 and recurring_amount to 800_00`() {
        val store = storeProfile(icaAccepted = true)
        val capturedSubscription = slot<MerchantSubscription>()
        setupHappyPath(store, capturedSubscription)

        val result = service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)

        assertEquals(MerchantSubscriptionPricing.PREMIUM_1_RANDS, capturedSubscription.captured.amountRands)
        assertEquals("800.00", result["recurring_amount"])
        assertEquals("800.00", result["amount"])
    }

    @Test
    fun `initiateCheckout sets PREMIUM_2 amount to 3000 and recurring_amount to 3000_00`() {
        val store = storeProfile(icaAccepted = true)
        val capturedSubscription = slot<MerchantSubscription>()
        setupHappyPath(store, capturedSubscription)

        val result = service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_2)

        assertEquals("3000.00", result["recurring_amount"])
        assertEquals("3000.00", result["amount"])
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Signature must be present in result
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout includes a valid MD5 signature in result`() {
        val store = storeProfile(icaAccepted = true)
        val capturedSubscription = slot<MerchantSubscription>()
        setupHappyPath(store, capturedSubscription)

        val result = service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)

        assertNotNull(result["signature"], "signature must be present in PayFast params")
        assertEquals(32, result["signature"]!!.length, "signature must be 32-char MD5 hex")
        // The signature must validate against the returned params using the same passphrase
        assertTrue(signatureUtil.isValidSignature(result), "signature in result must be valid")
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Required PayFast subscription fields
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout result contains all required PayFast subscription params`() {
        val store = storeProfile(icaAccepted = true)
        val capturedSubscription = slot<MerchantSubscription>()
        setupHappyPath(store, capturedSubscription)

        val result = service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)

        // Required merchant params
        assertNotNull(result["merchant_id"])
        assertNotNull(result["merchant_key"])
        // Required subscription params
        assertEquals("1", result["subscription_type"])
        assertEquals("3", result["frequency"])
        assertEquals("0", result["cycles"])
        assertNotNull(result["billing_date"])
        assertNotNull(result["recurring_amount"])
        // Transaction params
        assertNotNull(result["m_payment_id"])
        assertNotNull(result["amount"])
        assertNotNull(result["item_name"])
        // URL params
        assertNotNull(result["return_url"])
        assertNotNull(result["cancel_url"])
        assertNotNull(result["notify_url"])
    }

    @Test
    fun `initiateCheckout result does NOT contain passphrase`() {
        val store = storeProfile(icaAccepted = true)
        val capturedSubscription = slot<MerchantSubscription>()
        setupHappyPath(store, capturedSubscription)

        val result = service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)

        // passphrase is used to compute signature but must not be in the response
        assertFalse(result.containsKey("passphrase"),
            "passphrase must NOT be returned in the form params (SEC-TB01-03)")
        assertFalse(result.values.any { it == properties.passphrase },
            "passphrase value must not appear in any response value")
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // PENDING_PAYMENT subscription created with correct fields
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `initiateCheckout saves subscription with PENDING_PAYMENT status and correct storeId`() {
        val store = storeProfile(icaAccepted = true)
        val capturedSubscription = slot<MerchantSubscription>()
        setupHappyPath(store, capturedSubscription)

        service.initiateCheckout("store-1", "owner-1", SubscriptionTier.PREMIUM_1)

        verify(exactly = 1) { subscriptionRepository.save(capture(capturedSubscription)) }
        with(capturedSubscription.captured) {
            assertEquals("store-1", storeId)
            assertEquals(MerchantSubscriptionStatus.PENDING_PAYMENT, status)
            assertEquals(SubscriptionTier.PREMIUM_1, tier)
            assertNotNull(createdDate)
            assertNull(activatedDate)
            assertNull(payFastToken)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun setupHappyPath(
        store: io.curiousoft.izinga.commons.model.StoreProfile,
        capturedSubscription: CapturingSlot<MerchantSubscription>
    ) {
        every { storeRepository.findById("store-1") } returns Optional.of(store)
        every {
            subscriptionRepository.findByStoreIdAndStatus("store-1", MerchantSubscriptionStatus.ACTIVE)
        } returns emptyList()
        every {
            subscriptionRepository.findByStoreIdAndStatus("store-1", MerchantSubscriptionStatus.PENDING_PAYMENT)
        } returns emptyList()
        every { subscriptionRepository.save(capture(capturedSubscription)) } answers { firstArg() }
        every { userProfileRepo.findById(any()) } returns Optional.empty()
    }

    private fun storeProfile(icaAccepted: Boolean?): io.curiousoft.izinga.commons.model.StoreProfile {
        val store = io.curiousoft.izinga.commons.model.StoreProfile(
            io.curiousoft.izinga.commons.model.StoreType.FOOD,
            "Test Store",
            "test-store-1",
            "1 Test St",
            "https://img.test/s.png",
            "0811111111",
            mutableListOf("food"),
            mutableListOf(io.curiousoft.izinga.commons.model.BusinessHours(
                java.time.DayOfWeek.MONDAY, java.util.Date(), java.util.Date())),
            "owner-1",
            io.curiousoft.izinga.commons.model.Bank()
        )
        store.id = "store-1"
        store.icaAccepted = icaAccepted
        return store
    }

    private fun activeSubscription(storeId: String) = MerchantSubscription(
        id = "existing-active",
        storeId = storeId,
        ownerId = "owner-1",
        tier = SubscriptionTier.PREMIUM_1,
        status = MerchantSubscriptionStatus.ACTIVE,
        amountRands = BigDecimal("800"),
        createdDate = Date()
    )

    private fun pendingSubscription(storeId: String, createdMinutesAgo: Long) = MerchantSubscription(
        id = "existing-pending-${System.nanoTime()}",
        storeId = storeId,
        ownerId = "owner-1",
        tier = SubscriptionTier.PREMIUM_1,
        status = MerchantSubscriptionStatus.PENDING_PAYMENT,
        amountRands = BigDecimal("800"),
        createdDate = Date(System.currentTimeMillis() - createdMinutesAgo * 60_000L)
    )
}
