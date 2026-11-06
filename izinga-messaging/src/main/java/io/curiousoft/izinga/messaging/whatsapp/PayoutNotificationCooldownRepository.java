package io.curiousoft.izinga.messaging.whatsapp;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * ADR-028: Repository for {@link PayoutNotificationCooldown} throttle records.
 *
 * The {@code insert} method is used (not {@code save}) so that inserting a duplicate key
 * throws {@link org.springframework.dao.DuplicateKeyException}, which the caller uses to
 * detect that a cooldown is already active for the recipient.
 */
public interface PayoutNotificationCooldownRepository extends MongoRepository<PayoutNotificationCooldown, String> {
}
