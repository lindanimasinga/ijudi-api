package io.curiousoft.izinga.ordermanagement.auth;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Ephemeral MongoDB document for a WhatsApp OTP code.
 *
 * TTL: MongoDB will hard-delete documents where createdAt is older than 600 seconds
 * (~10 minutes) via a TTL index. This is a genuine hard-delete (TTL index runs ~1x/minute
 * sweep) — NOT a soft-delete flag. See WhatsAppOtpRepository for the TTL index definition.
 *
 * codeHash: SHA-256(mobileNumber + ":" + plainCode). Never stored plaintext.
 * attemptCount: incremented on each failed verify attempt; code is invalidated at 5.
 * used: set to true atomically (findOneAndUpdate with { used: false } filter) on
 *       successful verify — prevents replay within the TTL window. SEC-03.
 */
@Data
@Document(collection = "whatsapp_otp")
public class WhatsAppOtpDocument {

    @Id
    private String id;

    /** Normalized mobile number (+27XXXXXXXXX). Indexed for lookup. */
    @Indexed
    private String mobileNumber;

    /** SHA-256 hash of (mobileNumber + ":" + plainCode). */
    private String codeHash;

    /**
     * Hard-delete TTL anchor. MongoDB TTL index expires documents 600 s after this value.
     * Annotated here for documentation; the actual index is created via @CompoundIndex or
     * ensureIndex in WhatsAppOtpRepositoryCustomImpl.
     */
    @Indexed(expireAfterSeconds = 600)
    private Instant createdAt;

    /** Failed verify attempts against this code. Invalidate at >= 5. */
    private int attemptCount;

    /** True once this code has been successfully consumed. SEC-03: set atomically. */
    private boolean used;
}
