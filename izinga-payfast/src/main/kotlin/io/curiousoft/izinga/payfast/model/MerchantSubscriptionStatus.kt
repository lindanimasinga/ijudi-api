package io.curiousoft.izinga.payfast.model

/**
 * TIER-BILLING-01 / REQ-03: Lifecycle states for a PayFast-backed merchant subscription.
 * This enum is local to izinga-payfast — it is NOT added to izinga-commons in Phase 1.
 */
enum class MerchantSubscriptionStatus {
    /** Checkout session created; awaiting PayFast ITN confirmation of first payment. */
    PENDING_PAYMENT,

    /** PayFast ITN with payment_status=COMPLETE received and validated. Store tier updated. */
    ACTIVE,

    /** Subscription paused by PayFast (e.g. insufficient funds; will retry). */
    PAUSED,

    /** PayFast ITN with payment_status=FAILED received. */
    PAYMENT_FAILED,

    /** Subscription explicitly cancelled (populated cancelledDate and cancelledReason). */
    CANCELLED
}
