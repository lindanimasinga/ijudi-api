package io.curiousoft.izinga.payfast.service

import io.curiousoft.izinga.payfast.config.PayFastProperties
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionActivatedEvent
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionStatus
import io.curiousoft.izinga.payfast.repo.MerchantSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

/**
 * TIER-BILLING-01 / REQ-04, T-07: Processes PayFast Instant Transaction Notifications (ITNs).
 *
 * GATE (a) STATUS: Implementation complete; awaiting Security & Compliance sign-off on:
 *   - SEC-TB01-01: ITN public endpoint + IP allowlisting recommendation
 *   - SEC-TB01-02: replay attack prevention adequacy (mPaymentId + payFastPaymentId dedup)
 *   - SEC-TB01-03: credential and token logging controls
 * Do NOT merge T-07 to develop until Gate (a) returns PASS or CONDITIONAL-PASS.
 *
 * Security controls implemented:
 * 1. PayFast MD5 signature validation (alphabetic sort, URL-encode, passphrase append).
 * 2. merchant_id verification against configured value.
 * 3. Replay prevention: mPaymentId + payFastPaymentId dedup check.
 * 4. payFastToken is NEVER logged (RISK-06 / SEC-TB01-03).
 *
 * The ITN endpoint always returns HTTP 200 to PayFast. If the signature is invalid or
 * the subscription cannot be found, the handler logs and returns without modifying state.
 * This prevents PayFast from treating a permanent error as a transient retry.
 */
class PayFastItnHandler(
    private val properties: PayFastProperties,
    private val signatureUtil: PayFastSignatureUtil,
    private val subscriptionRepository: MerchantSubscriptionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(PayFastItnHandler::class.java)

    /**
     * Processes a PayFast ITN payload.
     *
     * @param params all POST parameters from the ITN request body (key=value pairs).
     * @return true if the ITN was processed successfully; false if it was rejected or ignored.
     */
    fun handleItn(params: Map<String, String>): Boolean {
        // Step 1: Signature validation — mandatory (REQ-04, RISK-04).
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

        // Step 3: Replay prevention — check if this payFastPaymentId has already been processed.
        // REQ-04 / RISK-03 / SEC-TB01-02.
        val subscription = subscriptionRepository.findById(mPaymentId).orElse(null) ?: run {
            log.warn("No MerchantSubscription found for mPaymentId={} — ITN ignored", mPaymentId)
            return false
        }

        if (subscription.payFastPaymentId == payFastPaymentId
            && subscription.status == MerchantSubscriptionStatus.ACTIVE) {
            log.info("Duplicate ITN detected for mPaymentId={} payFastPaymentId={} — already ACTIVE, ignoring",
                mPaymentId, payFastPaymentId)
            return true
        }

        return when (paymentStatus) {
            "COMPLETE" -> processComplete(subscription, payFastPaymentId, params)
            "FAILED" -> processFailed(subscription, payFastPaymentId)
            else -> {
                log.info("ITN payment_status={} for mPaymentId={} — no action taken", paymentStatus, mPaymentId)
                true
            }
        }
    }

    private fun processComplete(
        subscription: io.curiousoft.izinga.payfast.model.MerchantSubscription,
        payFastPaymentId: String,
        params: Map<String, String>
    ): Boolean {
        val now = java.util.Date()

        // Parse next billing date from ITN if provided.
        val nextBillingDate = parseItnDate(params["billing_date"])

        // SEC-TB01-03 / RISK-06: payFastToken is stored but NEVER logged.
        val token = params["token"]

        subscription.status = MerchantSubscriptionStatus.ACTIVE
        subscription.activatedDate = now
        subscription.lastBillingDate = now
        subscription.nextBillingDate = nextBillingDate
        subscription.payFastPaymentId = payFastPaymentId
        subscription.payFastToken = token   // null-safe: token may be absent on first ITN

        subscriptionRepository.save(subscription)

        // Log success without the token value.
        log.info("MerchantSubscription ACTIVATED: mPaymentId={} storeId={} tier={} payFastPaymentId={}",
            subscription.id, subscription.storeId, subscription.tier, payFastPaymentId)

        // Publish domain event — StoreService listener updates StoreProfile.subscriptionTier.
        eventPublisher.publishEvent(MerchantSubscriptionActivatedEvent(
            storeId = subscription.storeId,
            tier = subscription.tier,
            ownerId = subscription.ownerId
        ))

        return true
    }

    private fun processFailed(
        subscription: io.curiousoft.izinga.payfast.model.MerchantSubscription,
        payFastPaymentId: String
    ): Boolean {
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
    private fun parseItnDate(dateStr: String?): java.util.Date? {
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
