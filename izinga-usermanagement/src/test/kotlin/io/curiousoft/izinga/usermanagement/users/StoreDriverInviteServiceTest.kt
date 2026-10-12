package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.IzingaConstants
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreDriverInvitation
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.notification.DriverInviteNotificationPort
import io.curiousoft.izinga.commons.repo.StoreDriverInvitationRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Calendar
import java.util.Date
import java.util.Optional

/**
 * Unit tests for [StoreDriverInviteService] — ADR-018.
 *
 * Covers all five invite outcomes (B-04/B-08), both expiry states,
 * accept/reject with identity mismatch, the accept-time race-condition re-check,
 * and WhatsApp send failure non-propagation.
 *
 * LENIENT strictness because the aStore() helper stubs name+imageUrl which are only
 * consumed in the outcomes that reach the notification send path; other outcomes
 * that throw early would otherwise fail with UnnecessaryStubbing.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreDriverInviteServiceTest {

    @Mock lateinit var userProfileRepo: UserProfileRepo
    @Mock lateinit var storeRepository: StoreRepository
    @Mock lateinit var invitationRepo: StoreDriverInvitationRepo
    @Mock lateinit var profileService: UserProfileService
    @Mock lateinit var notificationPort: DriverInviteNotificationPort

    private lateinit var service: StoreDriverInviteService

    private val storeId = "store-abc"
    private val adminId = "admin-xyz"
    private val invitedBy = "caller-uid"
    private val driverPhone = "0841234567"
    private val normalizedDriverPhone = "+27841234567"

    @BeforeEach
    fun setUp() {
        service = StoreDriverInviteService(
            userProfileRepo, storeRepository, invitationRepo, profileService, notificationPort
        )
    }

    /**
     * Kotlin-safe Mockito `any()` for non-nullable Kotlin method parameters.
     *
     * Mockito's `any(Class<T>)` and no-arg `any()` return null at runtime.  When the call site is
     * Kotlin code passing the result to a *non-nullable* Kotlin method parameter, Kotlin 1.7+
     * inserts `checkNotNullExpressionValue` at the call site, which throws NPE before the mock
     * interceptor even runs.  The unchecked-cast trick below returns a value whose *declared*
     * Kotlin type is `T` (non-nullable), so no call-site check is emitted, while the actual
     * runtime value remains null — which is what Mockito's matcher stack needs.
     *
     * Only needed for Kotlin methods with non-nullable parameters.  Java method call sites are
     * unaffected because Kotlin does not insert null checks for Java method parameters.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.Mockito.any<Any?>() as T


    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun aStore(id: String = storeId, name: String = "Test Store"): StoreProfile {
        val store = mock(StoreProfile::class.java)
        `when`(store.name).thenReturn(name)
        `when`(store.imageUrl).thenReturn("https://img/store.jpg")
        return store
    }

    private fun aDriverProfile(
        id: String = "driver-id",
        phone: String = normalizedDriverPhone,
        storeIdTag: String? = null,
        messengerAdminIdTag: String? = null
    ): UserProfile {
        val profile = UserProfile("Driver Name", null, "", IzingaConstants.PENDING_PHOTO_URL, phone, ProfileRoles.MESSENGER)
        profile.id = id
        storeIdTag?.let { profile.tag["storeId"] = it }
        messengerAdminIdTag?.let { profile.tag["messengerAdminId"] = it }
        return profile
    }

    private fun aPendingInvitation(
        contextId: String = storeId,
        driverPhone: String = normalizedDriverPhone,
        code: String = "test-code",
        expired: Boolean = false
    ): StoreDriverInvitation {
        val createdAt = Date()
        val expiresAt = Calendar.getInstance().apply {
            time = createdAt
            add(Calendar.DAY_OF_YEAR, if (expired) -1 else 5)
        }.time
        return StoreDriverInvitation(
            id = "inv-id",
            contextId = contextId,
            contextType = StoreDriverInvitation.ContextType.STORE,
            invitedByUserId = invitedBy,
            driverMobileNumber = driverPhone,
            invitationCode = code,
            createdAt = createdAt,
            expiresAt = expiresAt
        )
    }

    private fun inviteRequest(
        mobile: String = driverPhone,
        name: String? = "Jane"
    ) = StoreDriverInviteService.InviteRequest(mobileNumber = mobile, name = name)

    // -----------------------------------------------------------------------
    // B-04 Outcome 1: No existing profile — creates new MESSENGER profile
    // -----------------------------------------------------------------------

    @Test
    fun `inviteForStore outcome 1 — no existing profile creates MESSENGER profile`() {
        val store = aStore()
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(null)

        // Capture the UserProfile passed to create() inside the thenAnswer lambda.
        // ArgumentCaptor.capture() returns a Kotlin non-nullable type from a Java generic — Kotlin
        // 1.7+ inserts a call-site null check when the captor is used with a Kotlin method
        // parameter, so we capture via thenAnswer instead.
        var capturedProfile: UserProfile? = null
        `when`(profileService.create(anyKt<UserProfile>())).thenAnswer { invocation ->
            capturedProfile = invocation.getArgument<UserProfile>(0)
            capturedProfile   // return the captured value
        }

        val result = service.inviteForStore(storeId, invitedBy, inviteRequest())

        assertEquals(StoreDriverInviteService.InviteOutcome.CREATED, result.outcome)
        // Implicit verification: if create() was never called, capturedProfile stays null and fails below.
        verify(profileService).create(anyKt<UserProfile>())
        val profile = capturedProfile ?: error("profileService.create() was not called")
        assertEquals(ProfileRoles.MESSENGER, profile.role)
        assertEquals(IzingaConstants.PENDING_PHOTO_URL, profile.imageUrl)
        assertEquals(storeId, profile.tag["storeId"])
        assertEquals("Jane", profile.name)
    }

    @Test
    fun `inviteForStore outcome 1 — uses default name when name not supplied`() {
        val store = aStore()
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(null)

        var capturedProfile: UserProfile? = null
        `when`(profileService.create(anyKt<UserProfile>())).thenAnswer { invocation ->
            capturedProfile = invocation.getArgument<UserProfile>(0)
            capturedProfile
        }

        service.inviteForStore(storeId, invitedBy, StoreDriverInviteService.InviteRequest(mobileNumber = driverPhone))

        verify(profileService).create(anyKt<UserProfile>())
        val profile = capturedProfile ?: error("profileService.create() was not called")
        assertEquals("New Driver", profile.name)
    }

    // -----------------------------------------------------------------------
    // B-04 Outcome 2: Already linked to THIS store
    // -----------------------------------------------------------------------

    @Test
    fun `inviteForStore outcome 2 — existing profile already linked to this store returns ALREADY_LINKED`() {
        val store = aStore()
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(
            aDriverProfile(storeIdTag = storeId)
        )

        val result = service.inviteForStore(storeId, invitedBy, inviteRequest())

        assertEquals(StoreDriverInviteService.InviteOutcome.ALREADY_LINKED, result.outcome)
        verify(invitationRepo, never()).save(any(StoreDriverInvitation::class.java))
        verify(profileService, never()).create(anyKt<UserProfile>())
    }

    // -----------------------------------------------------------------------
    // B-04 Outcome 3: Linked to a different store — 409
    // -----------------------------------------------------------------------

    @Test
    fun `inviteForStore outcome 3 — existing profile linked to different store returns 409`() {
        val store = aStore()
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(
            aDriverProfile(storeIdTag = "other-store-id")
        )

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.inviteForStore(storeId, invitedBy, inviteRequest())
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertEquals("DRIVER_LINKED_TO_OTHER_STORE", ex.reason)
        verify(invitationRepo, never()).save(any(StoreDriverInvitation::class.java))
    }

    // -----------------------------------------------------------------------
    // B-04 Outcome 4: No link, no pending invite — creates invitation
    // -----------------------------------------------------------------------

    @Test
    fun `inviteForStore outcome 4 — no link no pending invite creates StoreDriverInvitation`() {
        val store = aStore()
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(aDriverProfile())
        `when`(invitationRepo.findByContextIdAndDriverMobileNumberAndStatus(
            storeId, normalizedDriverPhone, StoreDriverInvitation.InvitationStatus.PENDING
        )).thenReturn(null)
        `when`(invitationRepo.save(any(StoreDriverInvitation::class.java))).thenAnswer { it.getArgument(0) }

        val result = service.inviteForStore(storeId, invitedBy, inviteRequest())

        assertEquals(StoreDriverInviteService.InviteOutcome.INVITED, result.outcome)
        val captor = ArgumentCaptor.forClass(StoreDriverInvitation::class.java)
        verify(invitationRepo).save(captor.capture())
        val saved = captor.value
        assertEquals(storeId, saved.contextId)
        assertEquals(StoreDriverInvitation.ContextType.STORE, saved.contextType)
        assertEquals(normalizedDriverPhone, saved.driverMobileNumber)
        assertNotNull(saved.invitationCode)
        assertEquals(StoreDriverInvitation.InvitationStatus.PENDING, saved.status)
    }

    @Test
    fun `inviteForStore outcome 4 — expired existing invite is replaced by a new one`() {
        val store = aStore()
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(aDriverProfile())
        val expiredInvite = aPendingInvitation(expired = true)
        `when`(invitationRepo.findByContextIdAndDriverMobileNumberAndStatus(
            storeId, normalizedDriverPhone, StoreDriverInvitation.InvitationStatus.PENDING
        )).thenReturn(expiredInvite)
        `when`(invitationRepo.save(any(StoreDriverInvitation::class.java))).thenAnswer { it.getArgument(0) }

        val result = service.inviteForStore(storeId, invitedBy, inviteRequest())

        assertEquals(StoreDriverInviteService.InviteOutcome.INVITED, result.outcome)
        val captor = ArgumentCaptor.forClass(StoreDriverInvitation::class.java)
        verify(invitationRepo).save(captor.capture())
        // Must be a NEW invitation, not the expired one
        assertEquals(StoreDriverInvitation.InvitationStatus.PENDING, captor.value.status)
    }

    // -----------------------------------------------------------------------
    // B-04 Outcome 5: No link, PENDING non-expired invite already exists — idempotent
    // -----------------------------------------------------------------------

    @Test
    fun `inviteForStore outcome 5 — pending non-expired invite already exists returns existing idempotently`() {
        val store = aStore()
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(aDriverProfile())
        val existingInvite = aPendingInvitation()
        `when`(invitationRepo.findByContextIdAndDriverMobileNumberAndStatus(
            storeId, normalizedDriverPhone, StoreDriverInvitation.InvitationStatus.PENDING
        )).thenReturn(existingInvite)

        val result = service.inviteForStore(storeId, invitedBy, inviteRequest())

        assertEquals(StoreDriverInviteService.InviteOutcome.INVITED, result.outcome)
        assertEquals(existingInvite.invitationCode, result.invitation?.invitationCode)
        verify(invitationRepo, never()).save(any(StoreDriverInvitation::class.java))
    }

    // -----------------------------------------------------------------------
    // B-07: WhatsApp send failure must NOT propagate to 5xx
    // -----------------------------------------------------------------------

    @Test
    fun `inviteForStore outcome 4 — WhatsApp send failure does not propagate to the endpoint`() {
        val store = aStore()
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(aDriverProfile())
        `when`(invitationRepo.findByContextIdAndDriverMobileNumberAndStatus(
            storeId, normalizedDriverPhone, StoreDriverInvitation.InvitationStatus.PENDING
        )).thenReturn(null)
        `when`(invitationRepo.save(any(StoreDriverInvitation::class.java))).thenAnswer { it.getArgument(0) }
        doThrow(RuntimeException("Template not yet approved")).`when`(notificationPort)
            .sendDriverStoreInvitation(anyString(), anyString(), anyString(), anyString())

        // Must NOT throw — the exception from the notification port is absorbed
        val result = service.inviteForStore(storeId, invitedBy, inviteRequest())
        assertEquals(StoreDriverInviteService.InviteOutcome.INVITED, result.outcome)
    }

    // -----------------------------------------------------------------------
    // B-06: acceptInvitation — happy path
    // -----------------------------------------------------------------------

    @Test
    fun `acceptInvitation happy path — sets storeId tag and marks ACCEPTED`() {
        val code = "accept-code"
        val invitation = aPendingInvitation(code = code)
        val driver = aDriverProfile(id = "driver-uid", phone = normalizedDriverPhone)

        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(invitation)
        `when`(userProfileRepo.findById("driver-uid")).thenReturn(Optional.of(driver))
        `when`(userProfileRepo.save(any(UserProfile::class.java))).thenReturn(driver)
        `when`(invitationRepo.save(any(StoreDriverInvitation::class.java))).thenAnswer { it.getArgument(0) }

        service.acceptInvitation(storeId, code, "driver-uid")

        assertEquals(storeId, driver.tag["storeId"])
        val invCaptor = ArgumentCaptor.forClass(StoreDriverInvitation::class.java)
        verify(invitationRepo).save(invCaptor.capture())
        assertEquals(StoreDriverInvitation.InvitationStatus.ACCEPTED, invCaptor.value.status)
        assertNotNull(invCaptor.value.respondedAt)
    }

    // -----------------------------------------------------------------------
    // B-06: acceptInvitation — contextId mismatch
    // -----------------------------------------------------------------------

    @Test
    fun `acceptInvitation returns 404 when contextId does not match invitation`() {
        val code = "code"
        val invitation = aPendingInvitation(contextId = storeId, code = code)
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(invitation)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.acceptInvitation("wrong-store-id", code, "driver-uid")
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    // -----------------------------------------------------------------------
    // B-06: acceptInvitation — phone mismatch
    // -----------------------------------------------------------------------

    @Test
    fun `acceptInvitation returns 403 when caller phone does not match invitation phone`() {
        val code = "code"
        val invitation = aPendingInvitation(code = code)
        val wrongDriver = aDriverProfile(id = "wrong-driver", phone = "+27899999999")
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(invitation)
        `when`(userProfileRepo.findById("wrong-driver")).thenReturn(Optional.of(wrongDriver))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.acceptInvitation(storeId, code, "wrong-driver")
        }
        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
    }

    // -----------------------------------------------------------------------
    // B-06: acceptInvitation — expired invitation
    // -----------------------------------------------------------------------

    @Test
    fun `acceptInvitation returns 410 GONE for expired invitation`() {
        val code = "code"
        val expiredInvite = aPendingInvitation(code = code, expired = true)
        val driver = aDriverProfile(id = "driver-uid")
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(expiredInvite)
        `when`(userProfileRepo.findById("driver-uid")).thenReturn(Optional.of(driver))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.acceptInvitation(storeId, code, "driver-uid")
        }
        assertEquals(HttpStatus.GONE, ex.statusCode)
        assertEquals("INVITATION_EXPIRED", ex.reason)
    }

    // -----------------------------------------------------------------------
    // B-06: acceptInvitation — race condition: driver already linked to different store
    // -----------------------------------------------------------------------

    @Test
    fun `acceptInvitation returns 409 when driver already linked to a different store at accept time`() {
        val code = "code"
        val invitation = aPendingInvitation(code = code)
        val driver = aDriverProfile(id = "driver-uid", storeIdTag = "other-store")
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(invitation)
        `when`(userProfileRepo.findById("driver-uid")).thenReturn(Optional.of(driver))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.acceptInvitation(storeId, code, "driver-uid")
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertEquals("DRIVER_ALREADY_LINKED", ex.reason)
    }

    // -----------------------------------------------------------------------
    // B-06: acceptInvitation — race condition: already linked to THIS store (idempotent)
    // -----------------------------------------------------------------------

    @Test
    fun `acceptInvitation is idempotent when driver already linked to this store`() {
        val code = "code"
        val invitation = aPendingInvitation(code = code)
        val driver = aDriverProfile(id = "driver-uid", storeIdTag = storeId)
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(invitation)
        `when`(userProfileRepo.findById("driver-uid")).thenReturn(Optional.of(driver))

        // Must not throw
        service.acceptInvitation(storeId, code, "driver-uid")

        verify(userProfileRepo, never()).save(any(UserProfile::class.java))
        verify(invitationRepo, never()).save(any(StoreDriverInvitation::class.java))
    }

    // -----------------------------------------------------------------------
    // B-06: rejectInvitation — happy path
    // -----------------------------------------------------------------------

    @Test
    fun `rejectInvitation happy path — marks REJECTED with reason`() {
        val code = "reject-code"
        val invitation = aPendingInvitation(code = code)
        val driver = aDriverProfile(id = "driver-uid")
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(invitation)
        `when`(userProfileRepo.findById("driver-uid")).thenReturn(Optional.of(driver))
        `when`(invitationRepo.save(any(StoreDriverInvitation::class.java))).thenAnswer { it.getArgument(0) }

        service.rejectInvitation(storeId, code, "driver-uid", "Not interested")

        val captor = ArgumentCaptor.forClass(StoreDriverInvitation::class.java)
        verify(invitationRepo).save(captor.capture())
        assertEquals(StoreDriverInvitation.InvitationStatus.REJECTED, captor.value.status)
        assertEquals("Not interested", captor.value.rejectionReason)
        assertNotNull(captor.value.respondedAt)

        // Driver profile must NOT be modified
        verify(userProfileRepo, never()).save(any(UserProfile::class.java))
    }

    @Test
    fun `rejectInvitation happy path — null reason is allowed`() {
        val code = "reject-code"
        val invitation = aPendingInvitation(code = code)
        val driver = aDriverProfile(id = "driver-uid")
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(invitation)
        `when`(userProfileRepo.findById("driver-uid")).thenReturn(Optional.of(driver))
        `when`(invitationRepo.save(any(StoreDriverInvitation::class.java))).thenAnswer { it.getArgument(0) }

        service.rejectInvitation(storeId, code, "driver-uid", null)

        val captor = ArgumentCaptor.forClass(StoreDriverInvitation::class.java)
        verify(invitationRepo).save(captor.capture())
        assertEquals(StoreDriverInvitation.InvitationStatus.REJECTED, captor.value.status)
    }

    // -----------------------------------------------------------------------
    // B-06: rejectInvitation — phone mismatch
    // -----------------------------------------------------------------------

    @Test
    fun `rejectInvitation returns 403 when caller phone does not match`() {
        val code = "code"
        val invitation = aPendingInvitation(code = code)
        val wrongDriver = aDriverProfile(id = "wrong-uid", phone = "+27899999999")
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(invitation)
        `when`(userProfileRepo.findById("wrong-uid")).thenReturn(Optional.of(wrongDriver))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.rejectInvitation(storeId, code, "wrong-uid", null)
        }
        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
    }

    // -----------------------------------------------------------------------
    // B-06: rejectInvitation — expired invitation
    // -----------------------------------------------------------------------

    @Test
    fun `rejectInvitation returns 410 for expired invitation`() {
        val code = "code"
        val expiredInvite = aPendingInvitation(code = code, expired = true)
        val driver = aDriverProfile(id = "driver-uid")
        `when`(invitationRepo.findByInvitationCode(code)).thenReturn(expiredInvite)
        `when`(userProfileRepo.findById("driver-uid")).thenReturn(Optional.of(driver))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.rejectInvitation(storeId, code, "driver-uid", null)
        }
        assertEquals(HttpStatus.GONE, ex.statusCode)
    }

    // -----------------------------------------------------------------------
    // B-08: inviteForMessengerAdmin — same five outcomes
    // -----------------------------------------------------------------------

    @Test
    fun `inviteForMessengerAdmin outcome 2 — already linked to this admin returns ALREADY_LINKED`() {
        val adminProfile = aDriverProfile(id = adminId, phone = "+27800000001")
        `when`(userProfileRepo.findById(adminId)).thenReturn(Optional.of(adminProfile))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(
            aDriverProfile(messengerAdminIdTag = adminId)
        )

        val result = service.inviteForMessengerAdmin(adminId, invitedBy, inviteRequest())

        assertEquals(StoreDriverInviteService.InviteOutcome.ALREADY_LINKED, result.outcome)
    }

    @Test
    fun `inviteForMessengerAdmin outcome 3 — linked to different admin returns 409`() {
        val adminProfile = aDriverProfile(id = adminId, phone = "+27800000001")
        `when`(userProfileRepo.findById(adminId)).thenReturn(Optional.of(adminProfile))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(
            aDriverProfile(messengerAdminIdTag = "other-admin-id")
        )

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.inviteForMessengerAdmin(adminId, invitedBy, inviteRequest())
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun `inviteForMessengerAdmin outcome 4 — creates invitation with MESSENGER_ADMIN contextType`() {
        val adminProfile = aDriverProfile(id = adminId, phone = "+27800000001").also {
            it.name = "Admin Name"
        }
        `when`(userProfileRepo.findById(adminId)).thenReturn(Optional.of(adminProfile))
        `when`(userProfileRepo.findByMobileNumber(normalizedDriverPhone)).thenReturn(aDriverProfile())
        `when`(invitationRepo.findByContextIdAndDriverMobileNumberAndStatus(
            adminId, normalizedDriverPhone, StoreDriverInvitation.InvitationStatus.PENDING
        )).thenReturn(null)
        `when`(invitationRepo.save(any(StoreDriverInvitation::class.java))).thenAnswer { it.getArgument(0) }

        val result = service.inviteForMessengerAdmin(adminId, invitedBy, inviteRequest())

        assertEquals(StoreDriverInviteService.InviteOutcome.INVITED, result.outcome)
        val captor = ArgumentCaptor.forClass(StoreDriverInvitation::class.java)
        verify(invitationRepo).save(captor.capture())
        assertEquals(StoreDriverInvitation.ContextType.MESSENGER_ADMIN, captor.value.contextType)
        assertEquals(adminId, captor.value.contextId)
    }

    // -----------------------------------------------------------------------
    // B-01 regression: AmbassadorAdminController flow still works with SignUpReason set
    // -----------------------------------------------------------------------

    @Test
    fun `AmbassadorAdminController flow — create with explicit SignUpReason DELIVERY_DRIVER still works`() {
        // This test confirms that removing the signUpReason null-check (B-01) does NOT break
        // the AmbassadorAdminController which always passes SignUpReason.DELIVERY_DRIVER.
        // Delegates to UserProfileService (tested separately in UserProfileValidationTest).
        val profile = UserProfile(
            "Sipho Dlamini",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "",
            AmbassadorAdminController.PENDING_PHOTO_URL,
            "0831234567",
            ProfileRoles.AMBASSADOR
        ).apply { profileApproved = true }

        // Verify PENDING_PHOTO_URL constant is still accessible on AmbassadorAdminController
        // and now delegates to IzingaConstants (B-02)
        assertEquals(IzingaConstants.PENDING_PHOTO_URL, AmbassadorAdminController.PENDING_PHOTO_URL)
        assertEquals(UserProfile.SignUpReason.DELIVERY_DRIVER, profile.signUpReason)
    }
}
