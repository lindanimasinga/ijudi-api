package io.curiousoft.izinga.commons.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.util.Date

/**
 * Represents an invitation from a store (or messenger admin) to a driver to join their team.
 *
 * Dual-context usage:
 *  - Store invite (B-04): [contextId] holds the storeId; [contextType] = STORE.
 *  - Messenger-admin invite (B-08): [contextId] holds the messengerAdminId; [contextType] = MESSENGER_ADMIN.
 *
 * The field was originally named `storeId` in the ADR; a dedicated [contextType] + generic [contextId]
 * pattern was chosen here to support both contexts cleanly without forcing an awkward field reuse
 * or a parallel entity.  The MongoDB collection name stays `storeDriverInvitation` for backward
 * compatibility with any tooling that references it by name.
 */
@Document(collection = "storeDriverInvitation")
@CompoundIndexes(
    CompoundIndex(
        name = "contextId_mobile_status",
        def = "{'contextId': 1, 'driverMobileNumber': 1, 'status': 1}"
    )
)
data class StoreDriverInvitation(
    @Id val id: String? = null,

    /**
     * The ID of the entity that issued the invitation.
     *  - [ContextType.STORE]: the storeId of the inviting store.
     *  - [ContextType.MESSENGER_ADMIN]: the adminId of the inviting messenger-admin.
     *
     * Named `contextId` rather than `storeId` to avoid misleading callers when this entity is
     * reused for the messenger-admin invite flow (B-08).
     */
    val contextId: String,

    /** Whether this invitation was issued by a store or a messenger-admin. */
    val contextType: ContextType,

    val invitedByUserId: String,
    val driverMobileNumber: String,
    val driverUserId: String? = null,

    /** Unique deep-link code generated as a UUID at creation time. */
    @Indexed(unique = true)
    val invitationCode: String,

    var status: InvitationStatus = InvitationStatus.PENDING,

    val createdAt: Date = Date(),

    /** Invitation expires 5 days after creation. */
    val expiresAt: Date,

    var respondedAt: Date? = null,
    var rejectionReason: String? = null
) {
    enum class InvitationStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }

    enum class ContextType { STORE, MESSENGER_ADMIN }

    /**
     * True when the invitation is still PENDING but the expiry wall-clock time has passed.
     * Callers must treat an expired invitation as non-actionable and persist
     * [InvitationStatus.EXPIRED] if they want to materialise the state in the DB.
     */
    val isExpired: Boolean get() = status == InvitationStatus.PENDING && Date().after(expiresAt)
}
