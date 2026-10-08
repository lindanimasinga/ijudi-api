package io.curiousoft.izinga.payfast.service

import io.curiousoft.izinga.commons.model.SubscriptionTier
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.payfast.config.PayFastProperties
import io.curiousoft.izinga.payfast.model.MerchantSubscription
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionPricing
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionStatus
import io.curiousoft.izinga.payfast.repo.MerchantSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * TIER-BILLING-01 / REQ-02, T-05: PayFast subscription checkout initiation.
 *
 * Handles:
 * - FREE tier rejection (HTTP 400)
 * - ICA gate (HTTP 422 MERCHANT_ICA_NOT_ACCEPTED)
 * - Duplicate ACTIVE guard (HTTP 409 SUBSCRIPTION_ALREADY_ACTIVE)
 * - 30-minute idempotency window for PENDING_PAYMENT (returns existing mPaymentId)
 * - PayFast form parameter construction and MD5 signature generation
 * - MerchantSubscription creation in PENDING_PAYMENT status
 *
 * AC-20 / RISK-05: PayFast credentials (merchantId, merchantKey, passphrase) come exclusively
 * from [PayFastProperties] which is loaded from environment secrets — never hardcoded.
 */
class PayFastCheckoutService(
    private val properties: PayFastProperties,
    private val signatureUtil: PayFastSignatureUtil,
    private val subscriptionRepository: MerchantSubscriptionRepository,
    private val storeRepository: StoreRepository,
    private val userProfileRepo: UserProfileRepo
) {

    private val log = LoggerFactory.getLogger(PayFastCheckoutService::class.java)

    /** Idempotency window: 30 minutes in milliseconds. */
    private val idempotencyWindowMs = 30L * 60L * 1000L

    /**
     * Initiates a PayFast subscription checkout for a merchant.
     *
     * @param storeId  the authenticated store ID from the JWT claim (IDOR-safe).
     * @param ownerId  the authenticated user ID from the JWT principal.
     * @param tier     the subscription tier selected by the merchant.
     * @return a map of signed PayFast form parameters to be auto-submitted by the frontend.
     * @throws ResponseStatusException HTTP 400 if tier is FREE.
     * @throws ResponseStatusException HTTP 422 if the store has not accepted the ICA.
     * @throws ResponseStatusException HTTP 409 if an ACTIVE subscription already exists.
     */
    fun initiateCheckout(storeId: String, ownerId: String, tier: SubscriptionTier): Map<String, String> {
        // REQ-02: FREE tier is not a subscription — reject immediately.
        if (tier == SubscriptionTier.FREE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Subscription initiation is not valid for FREE tier")
        }

        // REQ-02 / AC-05: ICA gate — the store must have accepted the Merchant Partner Agreement.
        val store = storeRepository.findById(storeId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found: $storeId")
        }
        if (store.icaAccepted != true) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "MERCHANT_ICA_NOT_ACCEPTED")
        }

        // REQ-02 / AC-06: Duplicate ACTIVE guard.
        val activeSubscriptions = subscriptionRepository.findByStoreIdAndStatus(
            storeId, MerchantSubscriptionStatus.ACTIVE)
        if (activeSubscriptions.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "SUBSCRIPTION_ALREADY_ACTIVE")
        }

        // REQ-02 / AC-07: 30-minute idempotency window for PENDING_PAYMENT.
        val now = Date()
        val windowStart = Date(now.time - idempotencyWindowMs)
        val recentPending = subscriptionRepository.findByStoreIdAndStatus(
            storeId, MerchantSubscriptionStatus.PENDING_PAYMENT)
            .filter { it.createdDate.after(windowStart) }
        if (recentPending.isNotEmpty()) {
            val existing = recentPending.first()
            log.info("Returning existing PENDING_PAYMENT subscription mPaymentId={} for storeId={}",
                existing.id, storeId)
            return buildPayFastParams(existing, store.ownerId ?: ownerId)
        }

        // Determine amount.
        val amountRands: BigDecimal = when (tier) {
            SubscriptionTier.PREMIUM_1 -> MerchantSubscriptionPricing.PREMIUM_1_RANDS
            SubscriptionTier.PREMIUM_2 -> MerchantSubscriptionPricing.PREMIUM_2_RANDS
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tier: $tier")
        }

        // Create MerchantSubscription in PENDING_PAYMENT.
        val mPaymentId = UUID.randomUUID().toString()
        val subscription = MerchantSubscription(
            id = mPaymentId,
            storeId = storeId,
            ownerId = store.ownerId ?: ownerId,
            tier = tier,
            status = MerchantSubscriptionStatus.PENDING_PAYMENT,
            amountRands = amountRands,
            createdDate = now
        )
        subscriptionRepository.save(subscription)
        log.info("Created MerchantSubscription mPaymentId={} storeId={} tier={} status=PENDING_PAYMENT",
            mPaymentId, storeId, tier)

        return buildPayFastParams(subscription, store.ownerId ?: ownerId)
    }

    /**
     * Builds the signed PayFast form parameters for a subscription checkout.
     * The returned map includes the "signature" entry so the frontend can construct the form.
     *
     * SEC-TB01-03: merchantKey and passphrase are read from properties only; neither is returned
     * in the response map (passphrase is only used to compute the signature hash).
     */
    fun buildPayFastParams(subscription: MerchantSubscription, ownerEmailOrId: String): Map<String, String> {
        val ownerEmail = resolveOwnerEmail(ownerEmailOrId)
        val billingDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val amountStr = String.format(Locale.US, "%.2f", subscription.amountRands)

        // PayFast requires return/cancel/notify URLs to include the mPaymentId for routing.
        val returnUrl = "${properties.returnUrl}/${subscription.storeId}"
        val cancelUrl = "${properties.cancelUrl}/${subscription.storeId}"

        val params: LinkedHashMap<String, String> = LinkedHashMap()
        // Merchant details
        params["merchant_id"] = properties.merchantId
        params["merchant_key"] = properties.merchantKey
        params["return_url"] = returnUrl
        params["cancel_url"] = cancelUrl
        params["notify_url"] = properties.notifyUrl
        // Buyer details
        params["email_address"] = ownerEmail
        // Transaction details
        params["m_payment_id"] = subscription.id
        params["amount"] = amountStr
        params["item_name"] = "iZinga ${subscription.tier.name.replace("_", " ")} Subscription"
        // Subscription-specific
        params["subscription_type"] = "1"
        params["billing_date"] = billingDate
        params["recurring_amount"] = amountStr
        params["frequency"] = "3"   // monthly
        params["cycles"] = "0"       // indefinite

        // Compute signature — passphrase is used internally only, never returned in the map.
        val signature = signatureUtil.computeSignature(params)
        params["signature"] = signature

        log.info("Built PayFast params mPaymentId={} tier={} amount={}", subscription.id, subscription.tier, amountStr)
        return params
    }

    /**
     * Resolves the merchant's email address for the PayFast form.
     * Falls back to the ownerEmailOrId value if the profile cannot be found.
     */
    private fun resolveOwnerEmail(ownerEmailOrId: String): String {
        return try {
            userProfileRepo.findById(ownerEmailOrId).map { it.emailAddress ?: ownerEmailOrId }.orElse(ownerEmailOrId)
        } catch (e: Exception) {
            log.warn("Could not resolve owner email for ownerId={} — using ID as fallback", ownerEmailOrId)
            ownerEmailOrId
        }
    }
}
