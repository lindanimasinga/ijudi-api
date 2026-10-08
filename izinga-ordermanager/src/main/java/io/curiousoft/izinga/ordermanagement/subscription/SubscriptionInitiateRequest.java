package io.curiousoft.izinga.ordermanagement.subscription;

import io.curiousoft.izinga.commons.model.SubscriptionTier;

/**
 * TIER-BILLING-01 / REQ-02: Request body for POST /merchant/subscription/initiate.
 * Only the tier is accepted from the body — storeId is read from the JWT claim (IDOR prevention).
 */
public class SubscriptionInitiateRequest {

    private SubscriptionTier tier;

    public SubscriptionInitiateRequest() { }

    public SubscriptionInitiateRequest(SubscriptionTier tier) {
        this.tier = tier;
    }

    public SubscriptionTier getTier() { return tier; }
    public void setTier(SubscriptionTier tier) { this.tier = tier; }
}
