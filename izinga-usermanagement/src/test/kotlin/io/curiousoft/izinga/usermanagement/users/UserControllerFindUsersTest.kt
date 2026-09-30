package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

/**
 * REQ: store-linked drivers — GET /user?role=MESSENGER&storeId=... must route to
 * findMessengersByStoreId(), the storeId-scoped counterpart to the existing
 * messengerAdminId-scoped lookup, and never fall through to it.
 */
@ExtendWith(MockitoExtension::class)
class UserControllerFindUsersTest {

    @Mock lateinit var profileService: UserProfileService
    @Mock lateinit var userProfileRepo: UserProfileRepo
    @Mock lateinit var qrCodeService: QRCodeService
    @Mock lateinit var ambassadorPayoutRepo: AmbassadorPayoutRepository
    @Mock lateinit var referralCodeService: ReferralCodeService

    @InjectMocks
    lateinit var controller: UserController

    @Test
    fun `findUsers with role MESSENGER and storeId calls findMessengersByStoreId`() {
        `when`(profileService.findMessengersByStoreId("store-abc")).thenReturn(emptyList())

        controller.findUsers(
            includePendingUsers = true,
            role = ProfileRoles.MESSENGER,
            messengerAdminId = null,
            storeId = "store-abc",
            latitude = null,
            longitude = null,
            range = null
        )

        verify(profileService).findMessengersByStoreId("store-abc")
        verify(profileService, never()).findMessengersByAdminId(anyString())
    }

    @Test
    fun `findUsers with both messengerAdminId and storeId prefers messengerAdminId`() {
        `when`(profileService.findMessengersByAdminId("admin-1")).thenReturn(emptyList())

        controller.findUsers(
            includePendingUsers = true,
            role = ProfileRoles.MESSENGER,
            messengerAdminId = "admin-1",
            storeId = "store-abc",
            latitude = null,
            longitude = null,
            range = null
        )

        verify(profileService).findMessengersByAdminId("admin-1")
        verify(profileService, never()).findMessengersByStoreId(anyString())
    }
}
