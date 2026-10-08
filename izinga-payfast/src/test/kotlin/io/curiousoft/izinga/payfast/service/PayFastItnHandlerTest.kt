package io.curiousoft.izinga.payfast.service

import io.curiousoft.izinga.commons.model.SubscriptionTier
import io.curiousoft.izinga.payfast.config.PayFastProperties
import io.curiousoft.izinga.payfast.model.MerchantSubscription
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionActivatedEvent
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionStatus
import io.curiousoft.izinga.payfast.repo.MerchantSubscriptionRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.math.BigDecimal
import java.util.*

/**
 * TIER-BILLING-01 / T-08: Unit tests for [PayFastItnHandler].
 *
 * Security gate changes verified here (Gate (a) SEC-TB01):
 *
 * SEC-TB01-01-C/D: Server-to-server validate
 * - Validate VALID → proceed to activate subscription
 * - Validate INVALID → return false (HTTP 200, permanent rejection)
 * - Validate transient failure → PayFastValidateTransientException propagates (controller returns HTTP 500)
 *
 * SEC-TB01-02-A: Atomic idempotency
 * - COMPLETE with findAndModify returning a previous document → event fires once
 * - COMPLETE with findAndModify returning null (already ACTIVE / not found) → event NOT fired
 * - Concurrency simulation: two sequential calls to same mPaymentId → event fires exactly once
 *
 * SEC-TB01-03-B: payFastToken not logged (structural: handler never passes MerchantSubscription to log)
 *
 * Original coverage maintained:
 * - AC-12: Invalid signature → not modified + SIGNATURE_VALIDATION_FAILED
 * - Merchant ID mismatch rejection
 * - FAILED payment status → PAYMENT_FAILED status
 * - Missing required fields (m_payment_id, pf_payment_id, payment_status)
 * - Unknown payment_status → ignored, returns true
 */
class PayFastItnHandlerTest {

    private val subscriptionRepository: MerchantSubscriptionRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val validateClient: PayFastValidateClient = mockk()
    private val mongoTemplate: MongoTemplate = mockk()

    private val passphrase = "test-passphrase"
    private val merchantId = "16791971"

    private val properties = PayFastProperties(
        merchantId = merchantId,
        merchantKey = "test-merchant-key",
        passphrase = passphrase,
        baseUrl = "https://sandbox.payfast.co.za/eng/process",
        returnUrl = "https://biz.izinga.co.za/business/subscription-success",
        cancelUrl = "https://biz.izinga.co.za/business/subscription-cancel",
        notifyUrl = "https://api.izinga.co.za/merchant/subscription/itn",
        validateUrl = "https://sandbox.payfast.co.za/eng/query/validate",
        validateTimeoutSeconds = 5
    )
    private val signatureUtil = PayFastSignatureUtil(passphrase)

    private lateinit var handler: PayFastItnHandler

    @BeforeEach
    fun setUp() {
        handler = PayFastItnHandler(
            properties, signatureUtil, subscriptionRepository, eventPublisher, validateClient, mongoTemplate
        )
        every { eventPublisher.publishEvent(any<Any>()) } just Runs
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // SEC-TB01-01-C/D: Server-to-server validate — VALID path
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn COMPLETE with valid signature and validate VALID activates subscription and publishes event`() {
        val mPaymentId = "sub-001"
        val pfPaymentId = "pf-001"
        val previousSubscription = pendingSubscription(mPaymentId)

        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID
        every {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        } returns previousSubscription

        val params = validCompleteParams(mPaymentId, pfPaymentId)
        val result = handler.handleItn(params)

        assertTrue(result, "Valid COMPLETE ITN should return true")

        // findAndModify must be called for the COMPLETE path (atomic update)
        verify(exactly = 1) {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        }
        // subscriptionRepository.save must NOT be called for COMPLETE (atomic path)
        verify(exactly = 0) { subscriptionRepository.save(any()) }

        // Event must be published with correct data
        val eventCapture = slot<MerchantSubscriptionActivatedEvent>()
        verify { eventPublisher.publishEvent(capture(eventCapture)) }
        assertEquals("store-1", eventCapture.captured.storeId)
        assertEquals(SubscriptionTier.PREMIUM_1, eventCapture.captured.tier)
        assertEquals("owner-1", eventCapture.captured.ownerId)
    }

    @Test
    fun `handleItn COMPLETE stores payFastToken via atomic update (token in params, not logged)`() {
        val mPaymentId = "sub-token"
        val pfPaymentId = "pf-token"
        val previousSubscription = pendingSubscription(mPaymentId)

        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID
        // Capture the Update argument to verify payFastToken is included
        val updateSlot = slot<Update>()
        every {
            mongoTemplate.findAndModify(any<Query>(), capture(updateSlot), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        } returns previousSubscription

        val params = validCompleteParams(mPaymentId, pfPaymentId, token = "pf-token-abc123")
        handler.handleItn(params)

        // Token must be set in the atomic update (stored in MongoDB), never passed to a logger
        val updateDocument = updateSlot.captured.updateObject
        val setFields = updateDocument["\$set"] as? org.bson.Document
        assertNotNull(setFields, "Update must have \$set fields")
        assertEquals("pf-token-abc123", setFields!!["payFastToken"],
            "payFastToken must be set via atomic update")
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // SEC-TB01-01-C: Server-to-server validate — transient failure → HTTP 500
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn throws PayFastValidateTransientException when validate call fails transiently`() {
        val mPaymentId = "sub-transient"
        val pfPaymentId = "pf-transient"

        every { validateClient.validate(any()) } throws PayFastValidateTransientException(
            "PAYFAST_VALIDATE_CALL_FAILED: network error mPaymentId=$mPaymentId"
        )

        val params = validCompleteParams(mPaymentId, pfPaymentId)

        // Must propagate — controller will catch this and return HTTP 500
        assertThrows(PayFastValidateTransientException::class.java) {
            handler.handleItn(params)
        }

        // No state change: no findAndModify, no save, no event
        verify(exactly = 0) {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        }
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `handleItn throws PayFastValidateTransientException when validate returns 5xx`() {
        val mPaymentId = "sub-5xx"
        val pfPaymentId = "pf-5xx"
        val cause = org.springframework.web.client.HttpServerErrorException(
            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)

        every { validateClient.validate(any()) } throws PayFastValidateTransientException(
            "PAYFAST_VALIDATE_CALL_FAILED: PayFast returned 500 mPaymentId=$mPaymentId", cause)

        val params = validCompleteParams(mPaymentId, pfPaymentId)

        val thrown = assertThrows(PayFastValidateTransientException::class.java) {
            handler.handleItn(params)
        }
        assertNotNull(thrown.cause, "Transient exception must carry original cause")
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // SEC-TB01-01-C: Server-to-server validate — INVALID response → HTTP 200, no state change
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn returns false when validate returns INVALID (permanent rejection, no state change)`() {
        val mPaymentId = "sub-invalid"
        val pfPaymentId = "pf-invalid"

        every { validateClient.validate(any()) } returns PayFastValidateResult.INVALID

        val params = validCompleteParams(mPaymentId, pfPaymentId)
        val result = handler.handleItn(params)

        assertFalse(result, "INVALID validate result must return false (HTTP 200, not 500)")
        verify(exactly = 0) {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        }
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // SEC-TB01-02-A: Atomic idempotency — concurrency guarantee
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn COMPLETE returns true and fires event when findAndModify matches PENDING_PAYMENT`() {
        val mPaymentId = "sub-atomic-win"
        val pfPaymentId = "pf-atomic-win"
        val previousSubscription = pendingSubscription(mPaymentId)

        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID
        every {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        } returns previousSubscription

        val result = handler.handleItn(validCompleteParams(mPaymentId, pfPaymentId))

        assertTrue(result)
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `handleItn COMPLETE returns true and does NOT fire event when findAndModify returns null (already ACTIVE)`() {
        // Simulates the LOSING thread in a concurrent double-ITN scenario:
        // the atomic findAndModify finds no document in PENDING_PAYMENT because the winning
        // thread already transitioned it to ACTIVE.
        val mPaymentId = "sub-atomic-loss"
        val pfPaymentId = "pf-atomic-loss"

        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID
        every {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        } returns null  // no PENDING_PAYMENT document found → already processed

        val result = handler.handleItn(validCompleteParams(mPaymentId, pfPaymentId))

        assertTrue(result, "Idempotent skip must still return true (HTTP 200)")
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `handleItn COMPLETE atomic guarantee — first call activates, second call skips event (fires exactly once)`() {
        // This test proves the atomic guarantee at the unit level:
        // call 1 wins (findAndModify returns previous document → event fires)
        // call 2 loses (findAndModify returns null → no event)
        // Net: event fires exactly once across two calls.
        val mPaymentId = "sub-concurrent"
        val pfPaymentId = "pf-concurrent"
        val previousSubscription = pendingSubscription(mPaymentId)

        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID

        // First call wins the atomic update
        every {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        } returnsMany listOf(previousSubscription, null)

        val params = validCompleteParams(mPaymentId, pfPaymentId)

        val result1 = handler.handleItn(params)
        val result2 = handler.handleItn(params)

        assertTrue(result1, "First call must return true")
        assertTrue(result2, "Second call (duplicate) must also return true (idempotent)")

        // Event must fire exactly once — not twice — proving atomic prevention of double-activation
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
        verify(exactly = 2) {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // AC-12: Invalid signature → rejected
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn with invalid signature returns false and does not call validate or modify subscription`() {
        val params = mutableMapOf(
            "merchant_id" to merchantId,
            "m_payment_id" to "sub-001",
            "pf_payment_id" to "pf-001",
            "payment_status" to "COMPLETE",
            "signature" to "invalid-signature-value-00000000"
        )
        val result = handler.handleItn(params)

        assertFalse(result, "Invalid signature should return false")
        // Validate must NOT be called — signature check is the first gate
        verify(exactly = 0) { validateClient.validate(any()) }
        verify(exactly = 0) {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        }
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `handleItn with missing signature returns false without calling validate`() {
        val params = mapOf(
            "merchant_id" to merchantId,
            "m_payment_id" to "sub-001",
            "pf_payment_id" to "pf-001",
            "payment_status" to "COMPLETE"
            // no "signature" key
        )
        val result = handler.handleItn(params)
        assertFalse(result)
        verify(exactly = 0) { validateClient.validate(any()) }
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Merchant ID mismatch
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn with wrong merchant_id returns false without calling validate`() {
        val params = buildSignedParams(
            mapOf(
                "merchant_id" to "99999999",   // wrong merchant ID
                "m_payment_id" to "sub-001",
                "pf_payment_id" to "pf-001",
                "payment_status" to "COMPLETE"
            )
        )
        val result = handler.handleItn(params)
        assertFalse(result, "Wrong merchant_id should be rejected")
        verify(exactly = 0) { validateClient.validate(any()) }
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // FAILED payment status
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn FAILED updates subscription to PAYMENT_FAILED and does not publish event`() {
        val mPaymentId = "sub-fail"
        val pfPaymentId = "pf-fail"
        val subscription = pendingSubscription(mPaymentId)
        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID
        every { subscriptionRepository.findById(mPaymentId) } returns Optional.of(subscription)
        every { subscriptionRepository.save(any()) } answers { firstArg() }

        val params = buildSignedParams(mapOf(
            "merchant_id" to merchantId,
            "m_payment_id" to mPaymentId,
            "pf_payment_id" to pfPaymentId,
            "payment_status" to "FAILED"
        ))
        val result = handler.handleItn(params)

        assertTrue(result)
        val savedCapture = slot<MerchantSubscription>()
        verify { subscriptionRepository.save(capture(savedCapture)) }
        assertEquals(MerchantSubscriptionStatus.PAYMENT_FAILED, savedCapture.captured.status)
        assertEquals(pfPaymentId, savedCapture.captured.payFastPaymentId)
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `handleItn FAILED returns false when no subscription found for mPaymentId`() {
        val mPaymentId = "sub-fail-missing"
        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID
        every { subscriptionRepository.findById(mPaymentId) } returns Optional.empty()

        val params = buildSignedParams(mapOf(
            "merchant_id" to merchantId,
            "m_payment_id" to mPaymentId,
            "pf_payment_id" to "pf-fail",
            "payment_status" to "FAILED"
        ))
        val result = handler.handleItn(params)

        assertFalse(result)
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // COMPLETE for non-existent subscription — idempotent (atomic findAndModify returns null)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn COMPLETE for unknown mPaymentId returns true (idempotent — atomic skip)`() {
        // With the atomic approach, a COMPLETE ITN for a non-existent mPaymentId results in
        // findAndModify returning null (no PENDING_PAYMENT document). processComplete returns
        // true silently — this is correct, we return HTTP 200 to PayFast.
        val mPaymentId = "sub-missing-complete"
        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID
        every {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        } returns null

        val params = validCompleteParams(mPaymentId, "pf-001")
        val result = handler.handleItn(params)

        assertTrue(result, "COMPLETE for unknown mPaymentId should return true (idempotent HTTP 200)")
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Missing required fields
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn returns false when m_payment_id is absent`() {
        val params = buildSignedParams(mapOf(
            "merchant_id" to merchantId,
            "pf_payment_id" to "pf-001",
            "payment_status" to "COMPLETE"
            // no m_payment_id
        ))
        val result = handler.handleItn(params)
        assertFalse(result)
        verify(exactly = 0) { validateClient.validate(any()) }
    }

    @Test
    fun `handleItn returns false when pf_payment_id is absent`() {
        val params = buildSignedParams(mapOf(
            "merchant_id" to merchantId,
            "m_payment_id" to "sub-001",
            "payment_status" to "COMPLETE"
            // no pf_payment_id
        ))
        val result = handler.handleItn(params)
        assertFalse(result)
        verify(exactly = 0) { validateClient.validate(any()) }
    }

    @Test
    fun `handleItn returns false when payment_status is absent`() {
        val params = buildSignedParams(mapOf(
            "merchant_id" to merchantId,
            "m_payment_id" to "sub-001",
            "pf_payment_id" to "pf-001"
            // no payment_status
        ))
        val result = handler.handleItn(params)
        assertFalse(result)
        verify(exactly = 0) { validateClient.validate(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Unknown payment status → no action, returns true
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleItn with unknown payment_status returns true without modifying subscription`() {
        val mPaymentId = "sub-unknown"
        every { validateClient.validate(any()) } returns PayFastValidateResult.VALID

        val params = buildSignedParams(mapOf(
            "merchant_id" to merchantId,
            "m_payment_id" to mPaymentId,
            "pf_payment_id" to "pf-001",
            "payment_status" to "PENDING"  // unknown status — not COMPLETE or FAILED
        ))
        val result = handler.handleItn(params)

        assertTrue(result)
        verify(exactly = 0) { subscriptionRepository.findById(any()) }
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) {
            mongoTemplate.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), MerchantSubscription::class.java)
        }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Builds a valid signed COMPLETE ITN param map, using the test passphrase.
     */
    private fun validCompleteParams(
        mPaymentId: String,
        pfPaymentId: String,
        token: String? = null
    ): Map<String, String> {
        val base = mutableMapOf(
            "merchant_id" to merchantId,
            "m_payment_id" to mPaymentId,
            "pf_payment_id" to pfPaymentId,
            "payment_status" to "COMPLETE",
            "amount_gross" to "800.00",
            "billing_date" to "2026-11-01",
            "item_name" to "iZinga PREMIUM 1 Subscription"
        )
        if (token != null) base["token"] = token
        return buildSignedParams(base)
    }

    /**
     * Adds a valid signature to [params] using the test passphrase.
     *
     * Uses computeSignatureSorted (alphabetical, Scheme 2) to simulate PayFast's ITN signing
     * behaviour — PayFast computes ITN signatures alphabetically. isValidSignature also uses
     * alphabetical order, so this correctly simulates a real signed ITN payload.
     *
     * Do NOT change this back to computeSignature (insertion order / Scheme 1) — that would
     * cause all ITN handler tests to silently sign and verify with the same wrong ordering,
     * masking a real production signature mismatch when params are not in alphabetical order.
     */
    private fun buildSignedParams(params: Map<String, String>): Map<String, String> {
        val mutable = params.toMutableMap()
        mutable.remove("signature")
        val sig = signatureUtil.computeSignatureSorted(mutable)
        mutable["signature"] = sig
        return mutable
    }

    private fun pendingSubscription(mPaymentId: String) = MerchantSubscription(
        id = mPaymentId,
        storeId = "store-1",
        ownerId = "owner-1",
        tier = SubscriptionTier.PREMIUM_1,
        status = MerchantSubscriptionStatus.PENDING_PAYMENT,
        amountRands = BigDecimal("800"),
        createdDate = Date()
    )
}
