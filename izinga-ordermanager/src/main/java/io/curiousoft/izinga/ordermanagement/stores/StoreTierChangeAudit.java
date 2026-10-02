package io.curiousoft.izinga.ordermanagement.stores;

import io.curiousoft.izinga.commons.model.SubscriptionTier;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * ONB-02 T-09 / SEC-ONB02-03-E: Immutable audit record for every subscription tier change.
 *
 * <p>Uses a separate collection ({@code store_tier_change_audit}) from {@link StoreAgreementAudit}
 * as required by SEC-ONB02-03-E. Insert-only — never updated or deleted.
 */
@Document(collection = "store_tier_change_audit")
public class StoreTierChangeAudit {

    @Id
    private String id;

    /** MongoDB ID of the store whose tier changed. */
    private String storeId;

    /** Profile ID of the STORE_ADMIN or ADMIN who triggered the change. */
    private String changedByUserId;

    /** The tier before this change. Null if this is the first explicit tier assignment. */
    private SubscriptionTier fromTier;

    /** The new tier after this change. */
    private SubscriptionTier toTier;

    /** Server-side timestamp of the change — never the client's value. */
    private Date changedAt;

    public StoreTierChangeAudit() { }

    public StoreTierChangeAudit(String storeId,
                                 String changedByUserId,
                                 SubscriptionTier fromTier,
                                 SubscriptionTier toTier,
                                 Date changedAt) {
        this.storeId = storeId;
        this.changedByUserId = changedByUserId;
        this.fromTier = fromTier;
        this.toTier = toTier;
        this.changedAt = changedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public String getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(String changedByUserId) { this.changedByUserId = changedByUserId; }

    public SubscriptionTier getFromTier() { return fromTier; }
    public void setFromTier(SubscriptionTier fromTier) { this.fromTier = fromTier; }

    public SubscriptionTier getToTier() { return toTier; }
    public void setToTier(SubscriptionTier toTier) { this.toTier = toTier; }

    public Date getChangedAt() { return changedAt; }
    public void setChangedAt(Date changedAt) { this.changedAt = changedAt; }
}
