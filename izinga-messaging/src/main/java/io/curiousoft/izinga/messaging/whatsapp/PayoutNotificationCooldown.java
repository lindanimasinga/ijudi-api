package io.curiousoft.izinga.messaging.whatsapp;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * ADR-028: Throttle record for missing-bank payout notifications.
 *
 * The document id encodes both the recipient and the notification type, e.g.:
 *   {@code "<messengerId>:DRIVER_MISSING_BANK"}
 *   {@code "<storeId>:STORE_MISSING_BANK"}
 *
 * The TTL index on {@link #createdAt} expires records after 24 hours, clearing the cooldown
 * automatically. Because TTL monitors run every 60 seconds (not exactly on the second), the
 * throttle logic also performs a staleness check in code — if the existing record's
 * {@link #createdAt} is older than 24 hours, it is overwritten and the notification is sent.
 *
 * Collection: {@code payout_notification_cooldowns}
 */
@Document(collection = "payout_notification_cooldowns")
public class PayoutNotificationCooldown {

    /** Compound key: {@code "<recipientId>:<NOTIFICATION_TYPE>"}. */
    @Id
    private String id;

    /**
     * When this cooldown was created. The TTL index on this field expires the document after
     * 86400 seconds (24 hours), automatically clearing the cooldown.
     */
    @Indexed(expireAfterSeconds = 86400)
    private Date createdAt;

    public PayoutNotificationCooldown() {
    }

    public PayoutNotificationCooldown(String id, Date createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
