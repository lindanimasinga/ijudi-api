package io.curiousoft.izinga.payfast.service

import io.curiousoft.izinga.payfast.config.PayFastProperties
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionActivatedEvent
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionStatus
import io.curiousoft.izinga.payfast.repo.MerchantSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.Date

/**
 * TIER-BILLING-01 / REQ-04, T-07: Processes PayFast Instant Transaction Notifications (ITNs).
 *
 * Security controls — ALL REQUIRED (Gate (a) SEC-TB01 binding):
 *
 * 1. (SEC-TB01-01-A) PayFast MD5 signature validation (alphabetic sort, URL-encode, passphrase append).
 * 2. (SEC-TB01-01-B) merchant_id verification against configured value (structured PAYFAST_IP_UNRECOGNIZED
 *    logging for IP mismatches is at the controller layer; PayFast IP list is config-driven).
 * 3. (SEC-TB01-01-C/D) Server-to-server validate: after signature passes, the ITN is posted to
 *    PayFast's /eng/query/validate. A transient failure (network, 5xx) throws
 *    [PayFastValidateTransientException] — the controller returns HTTP 500 so PayFast retries.
 *    A definitive INVALID response returns HTTP 200 (permanent rejection, no retries).
 *    There is NO bypass path in production for this call (SEC-TB01-01-D).
 * 4. (SEC-TB01-02-A) Atomic idempotency: processComplete uses MongoTemplate.findAndModify with
 *    status=PENDING_PAYMENT as a query condition. The event fires only when the atomic update
 *    succeeds (returns a document). Concurrent ITNs cannot both activate the same subscription.
 * 5. (SEC-TB01-03-B) payFastToken is NEVER passed to any log statement.
 *
 * HTTP response contract (SEC-TB01-01-C):
 *   - Signature fail / INVALID validate response → return false → controller returns HTTP 200.
 *   - Transient validate failure → throws PayFastValidateTransientException → controller returns HTTP 500.
 *   - All other paths → return true → controller returns HTTP 200.
 */
class PayFastItnHandler(
    private val properties: PayFastProperties,
    private val signatureUtil: PayFastSignatureUtil,
    private val subscriptionRepository: MerchantSubscriptionRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val validateClient: PayFastValidateClient,
    private val mongoTemplate: MongoTemplate
) {

    private val log = LoggerFactory.getLogger(PayFastItnHandler::class.java)

    /**
     * Processes a PayFast ITN payload.
     *
     * @param params all POST parameters from the ITN request body (key=value pairs).
     * @return true if the ITN was accepted (processed or idempotent skip); false if permanently rejected.
     * @throws PayFastValidateTransientException if the server-to-server validate call fails transiently
     *   — the caller (controller) must return HTTP 500 so PayFast retries.
     */
    fun handleItn(params: Map<String, String>): Boolean {
        // Step 1: Signature validation — mandatory (REQ-04, RISK-04).
        // Returns false (HTTP 200) for permanent signature failures.
        if (!signatureUtil.isValidSignature(params)) {
            log.warn("SIGNATURE_VALIDATION_FAILED for mPaymentId={} — ITN rejected",
                params["m_payment_id"])
            return false
        }

        // Step 2: merchant_id verification.
        val incomingMerchantId = params["merchant_id"]
        if (incomingMerchantId != properties.merchantId) {
            log.warn("MERCHANT_ID_MISMATCH: expected={} received={} — ITN rejected",
                properties.merchantId, incomingMerchantId)
            return false
        }

        // Step 3: Extract required fields.
        val mPaymentId = params["m_payment_id"] ?: run {
            log.warn("ITN missing m_payment_id — rejected")
            return false
        }
        val payFastPaymentId = params["pf_payment_id"] ?: run {
            log.warn("ITN missing pf_payment_id for mPaymentId={} — rejected", mPaymentId)
            return false
        }
        val paymentStatus = params["payment_status"] ?: run {
            log.warn("ITN missing payment_status for mPaymentId={} — rejected", mPaymentId)
            return false
        }

        // Step 4: Server-to-server validate (SEC-TB01-01-C, SEC-TB01-01-D).
        // VALID → proceed. INVALID → permanent rejection, return false (HTTP 200).
        // PayFastValidateTransientException → propagates to controller → HTTP 500 → PayFast retries.
        val validateResult = validateClient.validate(params)
        if (validateResult == PayFastValidateResult.INVALID) {
            log.warn("PAYFAST_VALIDATE_INVALID for mPaymentId={} pf_payment_id={} — ITN rejected (HTTP 200)",
                mPaymentId, payFastPaymentId)
            return false
        }

        return when (paymentStatus) {
            "COMPLETE" -> processComplete(mPaymentId, payFastPaymentId, params)
            "FAILED"   -> processFailed(mPaymentId, payFastPaymentId)
            else -> {
                log.info("ITN payment_status={} for mPaymentId={} — no action taken",
                    paymentStatus, mPaymentId)
                true
            }
        }
    }

    /**
     * Activates a subscription using an atomic MongoDB findAndModify.
     *
     * SEC-TB01-02-A: The query condition `status = PENDING_PAYMENT` is part of the atomic
     * operation. If two concurrent ITNs arrive for the same mPaymentId, only the first
     * findAndModify will find a document in PENDING_PAYMENT; the second returns null and
     * skips the event publication. This guarantees [MerchantSubscriptionActivatedEvent]
     * is published exactly once per mPaymentId.
     */
    private fun processComplete(
        mPaymentId: String,
        payFastPaymentId: String,
        params: Map<String, String>
    ): Boolean {
        val now = Date()
        val nextBillingDate = parseItnDate(params["billing_date"])
        // SEC-TB01-03-B: token is stored via atomic update — never passed to a log statement.
        val token = params["token"]

        val query = Query.query(
            Criteria.where("_id").`is`(mPaymentId)
                .and("status").`is`(MerchantSubscriptionStatus.PENDING_PAYMENT)
        )
        val update = Update()
            .set("status", MerchantSubscriptionStatus.ACTIVE)
            .set("activatedDate", now)
            .set("lastBillingDate", now)
            .set("nextBillingDate", nextBillingDate)
            .set("payFastPaymentId", payFastPaymentId)
            .set("payFastToken", token)  // stored but never logged

        // returnNew(false) → returns the BEFORE-update document (null if no match).
        val previous = mongoTemplate.findAndModify(
            query, update,
            FindAndModifyOptions.options().returnNew(false),
            io.curiousoft.izinga.payfast.model.MerchantSubscription::class.java
        )

        if (previous == null) {
            // Either already ACTIVE (duplicate ITN) or mPaymentId not found — idempotent skip.
            log.info("processComplete: mPaymentId={} not in PENDING_PAYMENT — idempotent skip " +
                "(already processed or not found)", mPaymentId)
            return true
        }

        // Log activation WITHOUT the token value (SEC-TB01-03-B).
        log.info("MerchantSubscription ACTIVATED: mPaymentId={} storeId={} tier={} payFastPaymentId={}",
            previous.id, previous.storeId, previous.tier, payFastPaymentId)

        // Publish domain event — StoreService listener updates StoreProfile.subscriptionTier.
        // This fires exactly once: only the findAndModify that matched PENDING_PAYMENT wins.
        eventPublisher.publishEvent(MerchantSubscriptionActivatedEvent(
            storeId = previous.storeId,
            tier = previous.tier,
            ownerId = previous.ownerId
        ))

        return true
    }

    private fun processFailed(mPaymentId: String, payFastPaymentId: String): Boolean {
        val subscription = subscriptionRepository.findById(mPaymentId).orElse(null) ?: run {
            log.warn("No MerchantSubscription found for mPaymentId={} in FAILED ITN — ignored",
                mPaymentId)
            return false
        }
        subscription.status = MerchantSubscriptionStatus.PAYMENT_FAILED
        subscription.payFastPaymentId = payFastPaymentId
        subscriptionRepository.save(subscription)

        log.warn("MerchantSubscription PAYMENT_FAILED: mPaymentId={} storeId={} payFastPaymentId={}",
            subscription.id, subscription.storeId, payFastPaymentId)

        return true
    }

    /**
     * Parses a PayFast billing_date string (format: yyyy-MM-dd HH:mm:ss or yyyy-MM-dd).
     * Returns null if parsing fails — the caller stores null for nextBillingDate.
     */
    private fun parseItnDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val formatFull = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val formatDate = java.text.SimpleDateFormat("yyyy-MM-dd")
            if (dateStr.contains(" ")) formatFull.parse(dateStr) else formatDate.parse(dateStr)
        } catch (e: Exception) {
            log.warn("Could not parse ITN billing_date='{}' — storing null", dateStr)
            null
        }
    }
}
