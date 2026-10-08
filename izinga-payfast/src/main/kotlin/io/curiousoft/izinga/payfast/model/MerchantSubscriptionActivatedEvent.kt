package io.curiousoft.izinga.payfast.model

import io.curiousoft.izinga.commons.model.SubscriptionTier

/**
 * TIER-BILLING-01 / REQ-05: Domain event published by [PayFastItnHandler] when a valid
 * PayFast ITN is received with payment_status=COMPLETE.
 *
 * The single [EventListener] in StoreService.onMerchantSubscriptionActivated() reacts to this
 * event and calls updateSubscriptionTier() to upgrade StoreProfile.subscriptionTier.
 */
data class MerchantSubscriptionActivatedEvent(
    /** The store whose subscription was activated. */
    val storeId: String,

    /** The tier activated (PREMIUM_1 or PREMIUM_2). */
    val tier: SubscriptionTier,

    /** The owner's user ID (for audit trail in StoreTierChangeAudit). */
    val ownerId: String
)
