package io.curiousoft.izinga.ordermanagement.stores;

import io.curiousoft.izinga.commons.model.SubscriptionTier;

/**
 * ONB-02 T-09 / SEC-ONB02-03-B: Dedicated DTO for PATCH /store/{id}/subscription-tier.
 *
 * <p>Contains ONLY the subscription tier. storeId is derived from the JWT claim server-side
 * (SEC-ONB02-03-A) — it must never be accepted from the request body.
 */
public class SubscriptionTierRequest {

    private SubscriptionTier subscriptionTier;

    public SubscriptionTier getSubscriptionTier() { return subscriptionTier; }
    public void setSubscriptionTier(SubscriptionTier subscriptionTier) { this.subscriptionTier = subscriptionTier; }
}
