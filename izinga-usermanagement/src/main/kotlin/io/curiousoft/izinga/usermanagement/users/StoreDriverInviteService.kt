package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.IzingaConstants
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreDriverInvitation
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.notification.DriverInviteNotificationPort
import io.curiousoft.izinga.commons.repo.StoreDriverInvitationRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.Calendar
import java.util.Date
import java.util.UUID

/**
 * Service for the Store Driver Invitation feature (ADR-018, B-04/B-06/B-08).
 *
 * Handles both the store-scoped invite flow ([ContextType.STORE]) and the
 * messenger-admin-scoped invite flow ([ContextType.MESSENGER_ADMIN]).
 *
 * All five invite outcomes (B-04/B-08 spec):
 *  1. No existing profile -> create driver profile, return CREATED
 *  2. Existing profile, already linked to THIS context -> return ALREADY_LINKED (idempotent)
 *  3. Existing profile, linked to a DIFFERENT context -> return HTTP 409
 *  4. Existing profile, not linked, no pending invite -> create invite + WhatsApp notification
 *  5. Existing profile, not linked, pending invite already exists -> return existing invite (idempotent)
 */
@Service
class StoreDriverInviteService(
    private val userProfileRepo: UserProfileRepo,
    private val storeRepository: StoreRepository,
    private val invitationRepo: StoreDriverInvitationRepo,
    private val profileService: UserProfileService,
    private val notificationPort: DriverInviteNotificationPort
) {

    private val log = LoggerFactory.getLogger(StoreDriverInviteService::class.java)

    data class InviteRequest(
        val mobileNumber: String,
        val name: String? = null,
        val surname: String? = null
    )

    data class InviteResult(
        val outcome: InviteOutcome,
        val invitation: StoreDriverInvitation? = null
    )

    enum class InviteOutcome { CREATED, ALREADY_LINKED, INVITED }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Process a store-scoped driver invitation (B-04).
     *
     * @param storeId    the store issuing the invitation (also the authorised caller's store)
     * @param invitedBy  Firebase UID of the authenticated store-admin user
     * @param request    invite request containing the driver's mobile number and optional name
     */
    fun inviteForStore(storeId: String, invitedBy: String, request: InviteRequest): InviteResult {
        val store = storeRepository.findById(storeId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found: $storeId")
        }
        return processInvite(
            contextId = storeId,
            contextType = StoreDriverInvitation.ContextType.STORE,
            contextName = store.name ?: storeId,
            invitedBy = invitedBy,
            request = request,
            tagKey = "storeId"
        )
    }

    /**
     * Process a messenger-admin-scoped driver invitation (B-08).
     *
     * @param adminId    the messenger-admin ID issuing the invitation
     * @param invitedBy  Firebase UID of the authenticated MESSENGER_ADMIN user
     * @param request    invite request containing the driver's mobile number and optional name
     */
    fun inviteForMessengerAdmin(adminId: String, invitedBy: String, request: InviteRequest): InviteResult {
        val admin = userProfileRepo.findById(adminId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Messenger admin not found: $adminId")
        }
        return processInvite(
            contextId = adminId,
            contextType = StoreDriverInvitation.ContextType.MESSENGER_ADMIN,
            contextName = admin.name ?: adminId,
            invitedBy = invitedBy,
            request = request,
            tagKey = "messengerAdminId"
        )
    }

    /**
     * Look up an invitation by code for the public deep-link landing page (B-05).
     * Returns null if not found.
     */
    fun findByCode(code: String): StoreDriverInvitation? =
        invitationRepo.findByInvitationCode(code)

    /**
     * Accept an invitation (B-06).
     *
     * Steps:
     *  1. Verify invitation exists and storeId matches path.
     *  2. Verify authenticated caller's mobile matches driverMobileNumber.
     *  3. Check expiry.
     *  4. Race-condition re-check: if driver is already linked to a different context, 409.
     *     If already linked to THIS context (duplicate accept), return 200 idempotently.
     *  5. Set tag[tagKey] = contextId on driver profile, mark invitation ACCEPTED.
     */
    fun acceptInvitation(contextId: String, code: String, callerUserId: String): Unit {
        val invitation = invitationRepo.findByInvitationCode(code)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")

        if (invitation.contextId != contextId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")
        }

        val driver = userProfileRepo.findById(callerUserId).orElseThrow {
            ResponseStatusException(HttpStatus.FORBIDDEN, "Caller profile not found")
        }

        val normalizedCallerPhone = normalizePhone(driver.mobileNumber)
        val normalizedInvitePhone = normalizePhone(invitation.driverMobileNumber)
        if (normalizedCallerPhone != normalizedInvitePhone) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Phone number does not match invitation")
        }

        if (invitation.isExpired) {
            throw ResponseStatusException(HttpStatus.GONE, "INVITATION_EXPIRED")
        }

        // Race-condition re-check (step 5 in B-06 spec)
        val tagKey = tagKeyForContextType(invitation.contextType)
        val existingLink = driver.tag[tagKey]
        if (existingLink != null) {
            if (existingLink == contextId) {
                // Idempotent: already linked to this context
                log.info("Driver {} already linked to contextId={} — accept is idempotent", callerUserId, contextId)
                return
            } else {
                throw ResponseStatusException(HttpStatus.CONFLICT, "DRIVER_ALREADY_LINKED")
            }
        }

        // Set link on driver profile
        driver.tag[tagKey] = contextId
        userProfileRepo.save(driver)

        // Mark invitation accepted
        val updated = invitation.copy(
            status = StoreDriverInvitation.InvitationStatus.ACCEPTED,
            respondedAt = Date()
        )
        invitationRepo.save(updated)

        log.info("Invitation {} accepted by driver {} for contextId={}", code, callerUserId, contextId)
    }

    /**
     * Reject an invitation (B-06).
     *
     * Same checks 1-4 as accept (no race-condition re-check needed).
     * Driver profile is NOT modified.
     */
    fun rejectInvitation(contextId: String, code: String, callerUserId: String, reason: String?): Unit {
        val invitation = invitationRepo.findByInvitationCode(code)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")

        if (invitation.contextId != contextId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")
        }

        val driver = userProfileRepo.findById(callerUserId).orElseThrow {
            ResponseStatusException(HttpStatus.FORBIDDEN, "Caller profile not found")
        }

        val normalizedCallerPhone = normalizePhone(driver.mobileNumber)
        val normalizedInvitePhone = normalizePhone(invitation.driverMobileNumber)
        if (normalizedCallerPhone != normalizedInvitePhone) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Phone number does not match invitation")
        }

        if (invitation.isExpired) {
            throw ResponseStatusException(HttpStatus.GONE, "INVITATION_EXPIRED")
        }

        val updated = invitation.copy(
            status = StoreDriverInvitation.InvitationStatus.REJECTED,
            respondedAt = Date(),
            rejectionReason = reason
        )
        invitationRepo.save(updated)

        log.info("Invitation {} rejected by driver {} for contextId={} reason={}", code, callerUserId, contextId, reason)
    }

    // -------------------------------------------------------------------------
    // Internal shared logic
    // -------------------------------------------------------------------------

    private fun processInvite(
        contextId: String,
        contextType: StoreDriverInvitation.ContextType,
        contextName: String,
        invitedBy: String,
        request: InviteRequest,
        tagKey: String
    ): InviteResult {
        val normalizedPhone = normalizePhoneForLookup(request.mobileNumber)
        val existing = userProfileRepo.findByMobileNumber(normalizedPhone)

        return if (existing == null) {
            // Outcome 1: no profile exists — create one
            val profile = UserProfile(
                name = request.name ?: "New Driver",
                signUpReason = null,
                address = "",
                imageUrl = IzingaConstants.PENDING_PHOTO_URL,
                mobileNumber = request.mobileNumber,
                role = ProfileRoles.MESSENGER
            ).apply {
                surname = request.surname
                tag[tagKey] = contextId
            }
            profileService.create(profile)
            log.info("Created new MESSENGER profile for mobileNumber={} contextType={} contextId={}", maskPhone(normalizedPhone), contextType, contextId)
            InviteResult(InviteOutcome.CREATED)
        } else {
            val existingLink = existing.tag[tagKey]
            when {
                existingLink == contextId -> {
                    // Outcome 2: already linked to this context
                    log.info("Driver mobileNumber={} already linked to contextId={} — returning ALREADY_LINKED", maskPhone(normalizedPhone), contextId)
                    InviteResult(InviteOutcome.ALREADY_LINKED)
                }
                existingLink != null -> {
                    // Outcome 3: linked to a different context
                    log.warn("Driver mobileNumber={} linked to different contextId={}, requested={}", maskPhone(normalizedPhone), existingLink, contextId)
                    throw ResponseStatusException(HttpStatus.CONFLICT, "DRIVER_LINKED_TO_OTHER_STORE")
                }
                else -> {
                    // Check for an existing non-expired PENDING invitation
                    val existingInvite = invitationRepo.findByContextIdAndDriverMobileNumberAndStatus(
                        contextId, normalizedPhone, StoreDriverInvitation.InvitationStatus.PENDING
                    )
                    if (existingInvite != null && !existingInvite.isExpired) {
                        // Outcome 5: valid pending invite already exists — return idempotently
                        log.info("Returning existing PENDING invitation {} for mobileNumber={} contextId={}", existingInvite.id, maskPhone(normalizedPhone), contextId)
                        InviteResult(InviteOutcome.INVITED, existingInvite)
                    } else {
                        // Outcome 4: create a new invitation.
                        // If there is an expired PENDING invitation, materialise it to EXPIRED before
                        // creating the replacement — prevents duplicate PENDING records accumulating.
                        existingInvite?.let {
                            invitationRepo.save(it.copy(status = StoreDriverInvitation.InvitationStatus.EXPIRED))
                            log.info("Materialised expired invitation {} to EXPIRED for mobileNumber={} contextId={}", it.id, maskPhone(normalizedPhone), contextId)
                        }
                        val invitationCode = UUID.randomUUID().toString()
                        val createdAt = Date()
                        val expiresAt = Calendar.getInstance().apply {
                            time = createdAt
                            add(Calendar.DAY_OF_YEAR, 5)
                        }.time

                        val invitation = StoreDriverInvitation(
                            contextId = contextId,
                            contextType = contextType,
                            invitedByUserId = invitedBy,
                            driverMobileNumber = normalizedPhone,
                            driverUserId = existing.id,
                            invitationCode = invitationCode,
                            createdAt = createdAt,
                            expiresAt = expiresAt
                        )
                        val saved = invitationRepo.save(invitation)
                        log.info("Created invitation {} for mobileNumber={} contextId={}", invitationCode, maskPhone(normalizedPhone), contextId)

                        // Send WhatsApp notification — non-fatal: template may not yet be approved
                        val driverName = existing.name ?: "Driver"
                        try {
                            notificationPort.sendDriverStoreInvitation(
                                driverMobileNumber = normalizedPhone,
                                driverName = driverName,
                                storeName = contextName,
                                invitationCode = invitationCode
                            )
                        } catch (e: Exception) {
                            log.error("WhatsApp invite notification failed for invitationCode={} — non-fatal: {}", invitationCode, e.message, e)
                        }

                        InviteResult(InviteOutcome.INVITED, saved)
                    }
                }
            }
        }
    }

    /**
     * Returns the tag key to use for a given [ContextType].
     *  - STORE -> "storeId"
     *  - MESSENGER_ADMIN -> "messengerAdminId"
     */
    private fun tagKeyForContextType(contextType: StoreDriverInvitation.ContextType): String = when (contextType) {
        StoreDriverInvitation.ContextType.STORE -> "storeId"
        StoreDriverInvitation.ContextType.MESSENGER_ADMIN -> "messengerAdminId"
    }

    /**
     * Normalize a phone number to +27XXXXXXXXX (E.164) format for lookup.
     * Uses the last 9 digits with a +27 prefix.
     * Throws 400 BAD_REQUEST (not 500) when the input is shorter than 9 characters.
     */
    private fun normalizePhoneForLookup(phone: String): String {
        if (phone.length < 9) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number too short: $phone")
        val last9 = phone.substring(phone.length - 9)
        return "+27$last9"
    }

    /** Masks all but the last 4 digits for safe log output: "+27****1234". */
    private fun maskPhone(phone: String): String {
        if (phone.length <= 4) return "****"
        return "****${phone.takeLast(4)}"
    }

    /**
     * Normalize a possibly-null phone number from a profile to a consistent format for comparison.
     * Returns empty string if null, to allow safe equality checks.
     */
    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        return if (phone.length >= 9) "+27${phone.substring(phone.length - 9)}" else phone
    }
}
