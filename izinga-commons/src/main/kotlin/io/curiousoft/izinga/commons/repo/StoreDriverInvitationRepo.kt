package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.StoreDriverInvitation
import org.springframework.data.mongodb.repository.MongoRepository

/**
 * Repository for [StoreDriverInvitation] documents.
 *
 * MongoDB indexes declared on the entity:
 *  - Unique index on `invitationCode` via [@Indexed(unique = true)] on the field.
 *  - Compound index on `(contextId, driverMobileNumber, status)` via [@CompoundIndex] on the class.
 */
interface StoreDriverInvitationRepo : MongoRepository<StoreDriverInvitation, String> {

    /** Look up an invitation by its deep-link code. Returns null if not found. */
    fun findByInvitationCode(code: String): StoreDriverInvitation?

    /**
     * Find an active (PENDING) invitation for a specific context + driver combination.
     * Used to enforce the idempotency check — do not create a duplicate if one already exists.
     */
    fun findByContextIdAndDriverMobileNumberAndStatus(
        contextId: String,
        mobileNumber: String,
        status: StoreDriverInvitation.InvitationStatus
    ): StoreDriverInvitation?

    /**
     * Find all invitations with the given status for a driver phone number across all contexts.
     * Used at accept-time to check whether the driver has any other pending invitations that
     * would conflict (e.g. accepted one context while this one is still open).
     */
    fun findByDriverMobileNumberAndStatus(
        mobileNumber: String,
        status: StoreDriverInvitation.InvitationStatus
    ): List<StoreDriverInvitation>
}
