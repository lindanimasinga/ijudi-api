package io.curiousoft.izinga.ordermanagement.auth;

/**
 * Custom repository operations for WhatsApp OTP documents.
 */
public interface WhatsAppOtpRepositoryCustom {

    /**
     * Atomically marks an OTP document as used if and only if it is currently unused.
     *
     * SEC-03: implemented as a single MongoDB findOneAndUpdate with filter
     * { _id: id, used: false } and update { $set: { used: true } }.
     * A no-match means the document was already used (or not found) — the caller
     * must treat this as a failure and not fall back to a read-then-write.
     *
     * @param id the document _id
     * @return the document as it was BEFORE the update (used=false), or null if
     *         the document was already used or not found.
     */
    WhatsAppOtpDocument atomicMarkUsed(String id);

    /**
     * Atomically increments the attempt counter for an OTP document.
     *
     * @param id the document _id
     * @return the updated document (with incremented attemptCount), or null if not found.
     */
    WhatsAppOtpDocument atomicIncrementAttempt(String id);
}
