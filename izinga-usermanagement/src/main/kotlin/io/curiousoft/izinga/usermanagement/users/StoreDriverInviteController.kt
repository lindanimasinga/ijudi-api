package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.StoreDriverInvitation
import io.curiousoft.izinga.commons.repo.StoreRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

data class DriverInviteRequest(
    val mobileNumber: String,
    val name: String? = null,
    val surname: String? = null
)

data class InviteOutcomeResponse(val outcome: String)

data class InviteCodeResponse(
    val storeId: String,
    val storeName: String?,
    val storeImageUrl: String?,
    val status: String,
    val expiresAt: String,
    /** Last 4 digits only, e.g. "****1234". */
    val driverMobileNumber: String
)

data class RejectRequest(val reason: String? = null)

// ---------------------------------------------------------------------------
// B-04: POST /store/{storeId}/invite
// ---------------------------------------------------------------------------

/**
 * Controller for store-scoped driver invitations (B-04) and invite accept/reject (B-06 store path).
 *
 * Auth:
 *  - POST /store/{storeId}/invite: STORE_ADMIN or ADMIN role
 *  - POST /store/{storeId}/invite/{code}/accept: any authenticated user (caller's mobile is verified
 *    against the invitation's driverMobileNumber inside the service)
 *  - POST /store/{storeId}/invite/{code}/reject: same as accept
 *  - GET  /invite/{code}: public (no auth)
 */
@RestController
@RequestMapping("/store/{storeId}/invite")
class StoreDriverInviteController(
    private val inviteService: StoreDriverInviteService,
    private val storeRepository: StoreRepository
) {

    private val log = LoggerFactory.getLogger(StoreDriverInviteController::class.java)

    /**
     * B-04: Invite a driver to join a store's team.
     *
     * Five outcomes mapped to HTTP codes:
     *  1. CREATED (new profile)       -> 201
     *  2. ALREADY_LINKED             -> 200
     *  3. Linked to different store  -> 409
     *  4. INVITED (new invitation)   -> 201
     *  5. INVITED (existing, idempotent) -> 200
     */
    @PostMapping(consumes = ["application/json"], produces = ["application/json"])
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    fun inviteDriver(
        @PathVariable storeId: String,
        @RequestBody request: DriverInviteRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<InviteOutcomeResponse> {
        log.info("POST /store/{}/invite mobileNumber={}", storeId, request.mobileNumber)
        val invitedBy = jwt.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No subject in JWT")
        val result = inviteService.inviteForStore(storeId, invitedBy, StoreDriverInviteService.InviteRequest(
            mobileNumber = request.mobileNumber,
            name = request.name,
            surname = request.surname
        ))
        val status = when (result.outcome) {
            StoreDriverInviteService.InviteOutcome.CREATED -> HttpStatus.CREATED
            StoreDriverInviteService.InviteOutcome.INVITED -> {
                if (result.invitation?.id == null) HttpStatus.CREATED else HttpStatus.OK
            }
            StoreDriverInviteService.InviteOutcome.ALREADY_LINKED -> HttpStatus.OK
        }
        return ResponseEntity.status(status).body(InviteOutcomeResponse(result.outcome.name))
    }

    /** B-06: Accept. */
    @PostMapping("/{code}/accept", produces = ["application/json"])
    @PreAuthorize("isAuthenticated()")
    fun acceptInvite(
        @PathVariable storeId: String,
        @PathVariable code: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Unit> {
        log.info("POST /store/{}/invite/{}/accept", storeId, code)
        val callerUserId = jwt.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No subject in JWT")
        inviteService.acceptInvitation(storeId, code, callerUserId)
        return ResponseEntity.ok().build()
    }

    /** B-06: Reject. */
    @PostMapping("/{code}/reject", produces = ["application/json"])
    @PreAuthorize("isAuthenticated()")
    fun rejectInvite(
        @PathVariable storeId: String,
        @PathVariable code: String,
        @RequestBody(required = false) body: RejectRequest?,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Unit> {
        log.info("POST /store/{}/invite/{}/reject", storeId, code)
        val callerUserId = jwt.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No subject in JWT")
        inviteService.rejectInvitation(storeId, code, callerUserId, body?.reason)
        return ResponseEntity.ok().build()
    }
}

// ---------------------------------------------------------------------------
// B-05: GET /invite/{code} — public deep-link landing
// ---------------------------------------------------------------------------

/**
 * Public endpoint for the WhatsApp URL button deep-link.
 *
 * A single-variable URL button can only carry one dynamic parameter, so the invitation link
 * uses the code alone as the variable (appended to the static prefix configured in the
 * Meta template).  No authentication required — the landing page uses the code to show
 * invite details before the driver logs in and accepts/rejects.
 */
@RestController
@RequestMapping("/invite")
class InviteCodeLookupController(
    private val inviteService: StoreDriverInviteService,
    private val storeRepository: StoreRepository
) {

    private val log = LoggerFactory.getLogger(InviteCodeLookupController::class.java)
    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    @GetMapping("/{code}", produces = ["application/json"])
    fun getInvite(@PathVariable code: String): ResponseEntity<InviteCodeResponse> {
        log.info("GET /invite/{}", code)
        val invitation = inviteService.findByCode(code)
            ?: return ResponseEntity.notFound().build()

        // Resolve store / admin name and imageUrl
        val (contextName, contextImageUrl) = when (invitation.contextType) {
            StoreDriverInvitation.ContextType.STORE -> {
                val store = storeRepository.findById(invitation.contextId).orElse(null)
                Pair(store?.name, store?.imageUrl)
            }
            StoreDriverInvitation.ContextType.MESSENGER_ADMIN -> {
                Pair(null, null)
            }
        }

        val maskedPhone = maskPhone(invitation.driverMobileNumber)
        val expiresAtIso = invitation.expiresAt.toInstant().atOffset(ZoneOffset.UTC).format(isoFormatter)

        return ResponseEntity.ok(InviteCodeResponse(
            storeId = invitation.contextId,
            storeName = contextName,
            storeImageUrl = contextImageUrl,
            status = invitation.status.name,
            expiresAt = expiresAtIso,
            driverMobileNumber = maskedPhone
        ))
    }

    /** Masks all but the last 4 digits: "****1234". */
    private fun maskPhone(phone: String): String {
        if (phone.length <= 4) return "****"
        return "****${phone.takeLast(4)}"
    }
}

// ---------------------------------------------------------------------------
// B-08: POST /messenger-admin/{adminId}/invite
// ---------------------------------------------------------------------------

/**
 * Messenger-admin-scoped driver invitation endpoint (B-08).
 *
 * Reuses [StoreDriverInviteService] with [StoreDriverInvitation.ContextType.MESSENGER_ADMIN]
 * and the "messengerAdminId" tag key.  Same five-outcome logic as B-04.
 *
 * Auth: MESSENGER_ADMIN role only.
 */
@RestController
@RequestMapping("/messenger-admin/{adminId}/invite")
class MessengerAdminDriverInviteController(
    private val inviteService: StoreDriverInviteService
) {

    private val log = LoggerFactory.getLogger(MessengerAdminDriverInviteController::class.java)

    @PostMapping(consumes = ["application/json"], produces = ["application/json"])
    @PreAuthorize("hasRole('MESSENGER_ADMIN')")
    fun inviteDriver(
        @PathVariable adminId: String,
        @RequestBody request: DriverInviteRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<InviteOutcomeResponse> {
        log.info("POST /messenger-admin/{}/invite mobileNumber={}", adminId, request.mobileNumber)
        val invitedBy = jwt.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No subject in JWT")
        val result = inviteService.inviteForMessengerAdmin(adminId, invitedBy, StoreDriverInviteService.InviteRequest(
            mobileNumber = request.mobileNumber,
            name = request.name,
            surname = request.surname
        ))
        val status = when (result.outcome) {
            StoreDriverInviteService.InviteOutcome.CREATED -> HttpStatus.CREATED
            StoreDriverInviteService.InviteOutcome.INVITED -> {
                if (result.invitation?.id == null) HttpStatus.CREATED else HttpStatus.OK
            }
            StoreDriverInviteService.InviteOutcome.ALREADY_LINKED -> HttpStatus.OK
        }
        return ResponseEntity.status(status).body(InviteOutcomeResponse(result.outcome.name))
    }

    /** B-06: Accept via messenger-admin invite code. */
    @PostMapping("/{code}/accept", produces = ["application/json"])
    @PreAuthorize("isAuthenticated()")
    fun acceptInvite(
        @PathVariable adminId: String,
        @PathVariable code: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Unit> {
        log.info("POST /messenger-admin/{}/invite/{}/accept", adminId, code)
        val callerUserId = jwt.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No subject in JWT")
        inviteService.acceptInvitation(adminId, code, callerUserId)
        return ResponseEntity.ok().build()
    }

    /** B-06: Reject via messenger-admin invite code. */
    @PostMapping("/{code}/reject", produces = ["application/json"])
    @PreAuthorize("isAuthenticated()")
    fun rejectInvite(
        @PathVariable adminId: String,
        @PathVariable code: String,
        @RequestBody(required = false) body: RejectRequest?,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Unit> {
        log.info("POST /messenger-admin/{}/invite/{}/reject", adminId, code)
        val callerUserId = jwt.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No subject in JWT")
        inviteService.rejectInvitation(adminId, code, callerUserId, body?.reason)
        return ResponseEntity.ok().build()
    }
}
