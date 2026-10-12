package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.StoreDriverInvitation
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.repo.StoreRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.util.Calendar
import java.util.Date
import java.util.Optional

/**
 * Unit tests for [InviteCodeLookupController.getInvite] — T-01 / ADR-018.
 *
 * Asserts that the [InviteCodeResponse.contextType] field is present and correct
 * for both STORE and MESSENGER_ADMIN invite types (REQ-01 from INVITE-DRV-01).
 *
 * Covers:
 *  - TC-INV-01: STORE contextType returned as "STORE" in response
 *  - TC-INV-02: MESSENGER_ADMIN contextType returned as "MESSENGER_ADMIN" in response
 *  - TC-INV-03: 404 when invite code not found
 *  - TC-INV-04: STORE invite populates storeName and storeImageUrl from store repo
 *  - TC-INV-05: MESSENGER_ADMIN invite returns null storeName and null storeImageUrl
 *  - TC-INV-06: driverMobileNumber is masked to last 4 digits
 *  - TC-INV-07: status field reflects invitation status name
 */
@ExtendWith(MockitoExtension::class)
class InviteCodeLookupControllerTest {

    @Mock lateinit var inviteService: StoreDriverInviteService
    @Mock lateinit var storeRepository: StoreRepository

    @InjectMocks
    lateinit var controller: InviteCodeLookupController

    private val storeId = "store-abc"
    private val adminId = "admin-xyz"
    private val driverPhone = "+27841234567"

    private fun anInvitation(
        contextId: String,
        contextType: StoreDriverInvitation.ContextType,
        code: String = "test-code",
        driverPhone: String = this.driverPhone,
        status: StoreDriverInvitation.InvitationStatus = StoreDriverInvitation.InvitationStatus.PENDING
    ): StoreDriverInvitation {
        val now = Date()
        val expiresAt = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, 5)
        }.time
        return StoreDriverInvitation(
            id = "inv-id",
            contextId = contextId,
            contextType = contextType,
            invitedByUserId = "inviter-uid",
            driverMobileNumber = driverPhone,
            invitationCode = code,
            createdAt = now,
            expiresAt = expiresAt,
            status = status
        )
    }

    private fun aStore(id: String = storeId): StoreProfile {
        val store = mock(StoreProfile::class.java)
        `when`(store.name).thenReturn("Test Store")
        `when`(store.imageUrl).thenReturn("https://img/store.jpg")
        return store
    }

    // -----------------------------------------------------------------------
    // TC-INV-01: STORE invite — contextType is "STORE"
    // -----------------------------------------------------------------------

    @Test
    fun `getInvite returns contextType STORE for store-issued invitation`() {
        val store = aStore()
        val invitation = anInvitation(contextId = storeId, contextType = StoreDriverInvitation.ContextType.STORE)
        `when`(inviteService.findByCode("test-code")).thenReturn(invitation)
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))

        val response = controller.getInvite("test-code")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("STORE", body.contextType)
    }

    // -----------------------------------------------------------------------
    // TC-INV-02: MESSENGER_ADMIN invite — contextType is "MESSENGER_ADMIN"
    // -----------------------------------------------------------------------

    @Test
    fun `getInvite returns contextType MESSENGER_ADMIN for messenger-admin-issued invitation`() {
        val invitation = anInvitation(contextId = adminId, contextType = StoreDriverInvitation.ContextType.MESSENGER_ADMIN)
        `when`(inviteService.findByCode("admin-code")).thenReturn(invitation)

        val response = controller.getInvite("admin-code")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("MESSENGER_ADMIN", body.contextType)
    }

    // -----------------------------------------------------------------------
    // TC-INV-03: 404 when invite code not found
    // -----------------------------------------------------------------------

    @Test
    fun `getInvite returns 404 when invitation not found`() {
        `when`(inviteService.findByCode("missing-code")).thenReturn(null)

        val response = controller.getInvite("missing-code")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNull(response.body)
    }

    // -----------------------------------------------------------------------
    // TC-INV-04: STORE invite populates storeName and storeImageUrl
    // -----------------------------------------------------------------------

    @Test
    fun `getInvite populates storeName and storeImageUrl from store repo for STORE context`() {
        val invitation = anInvitation(contextId = storeId, contextType = StoreDriverInvitation.ContextType.STORE)
        val store = aStore()
        `when`(inviteService.findByCode("test-code")).thenReturn(invitation)
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))

        val body = controller.getInvite("test-code").body!!

        assertEquals("Test Store", body.storeName)
        assertEquals("https://img/store.jpg", body.storeImageUrl)
        assertEquals(storeId, body.storeId)
    }

    // -----------------------------------------------------------------------
    // TC-INV-05: MESSENGER_ADMIN invite — storeName and storeImageUrl are null
    // -----------------------------------------------------------------------

    @Test
    fun `getInvite returns null storeName and storeImageUrl for MESSENGER_ADMIN context`() {
        val invitation = anInvitation(contextId = adminId, contextType = StoreDriverInvitation.ContextType.MESSENGER_ADMIN)
        `when`(inviteService.findByCode("admin-code")).thenReturn(invitation)

        val body = controller.getInvite("admin-code").body!!

        assertNull(body.storeName)
        assertNull(body.storeImageUrl)
        assertEquals(adminId, body.storeId)
    }

    // -----------------------------------------------------------------------
    // TC-INV-06: driverMobileNumber is masked to last 4 digits
    // -----------------------------------------------------------------------

    @Test
    fun `getInvite masks driver mobile number to last 4 digits`() {
        val store = aStore()
        val invitation = anInvitation(
            contextId = storeId,
            contextType = StoreDriverInvitation.ContextType.STORE,
            driverPhone = "+27841234567"
        )
        `when`(inviteService.findByCode("test-code")).thenReturn(invitation)
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))

        val body = controller.getInvite("test-code").body!!

        assertEquals("****4567", body.driverMobileNumber)
    }

    // -----------------------------------------------------------------------
    // TC-INV-07: status field reflects invitation status name
    // -----------------------------------------------------------------------

    @Test
    fun `getInvite returns status field matching invitation status name`() {
        val store = aStore()
        val invitation = anInvitation(
            contextId = storeId,
            contextType = StoreDriverInvitation.ContextType.STORE,
            status = StoreDriverInvitation.InvitationStatus.PENDING
        )
        `when`(inviteService.findByCode("test-code")).thenReturn(invitation)
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.of(store))

        val body = controller.getInvite("test-code").body!!

        assertEquals("PENDING", body.status)
    }

    // -----------------------------------------------------------------------
    // TC-INV-08: STORE invite with no store in repo — storeName and imageUrl are null
    // -----------------------------------------------------------------------

    @Test
    fun `getInvite returns null storeName and imageUrl when store not found in repo`() {
        val invitation = anInvitation(contextId = storeId, contextType = StoreDriverInvitation.ContextType.STORE)
        `when`(inviteService.findByCode("test-code")).thenReturn(invitation)
        `when`(storeRepository.findById(storeId)).thenReturn(Optional.empty())

        val body = controller.getInvite("test-code").body!!

        assertNull(body.storeName)
        assertNull(body.storeImageUrl)
        // contextType must still be correct even when store lookup finds nothing
        assertEquals("STORE", body.contextType)
    }
}
