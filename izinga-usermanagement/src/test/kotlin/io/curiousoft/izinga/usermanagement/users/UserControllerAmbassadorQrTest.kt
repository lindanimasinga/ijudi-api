package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.*
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserControllerAmbassadorQrTest {

    @Mock lateinit var profileService: UserProfileService
    @Mock lateinit var userProfileRepo: UserProfileRepo
    @Mock lateinit var qrCodeService: QRCodeService
    @Mock lateinit var ambassadorPayoutRepo: AmbassadorPayoutRepository

    @InjectMocks
    lateinit var controller: UserController

    @Test
    fun `getAmbassadorQrCode unknown user returns 404`() {
        `when`(userProfileRepo.findById("x")).thenReturn(Optional.empty())
        assertEquals(HttpStatus.NOT_FOUND, controller.getAmbassadorQrCode("x").statusCode)
        verifyNoInteractions(qrCodeService)
    }

    @Test
    fun `getAmbassadorQrCode non-ambassador role returns 403`() {
        val p = mock(UserProfile::class.java)
        `when`(p.role).thenReturn(ProfileRoles.MESSENGER)
        `when`(userProfileRepo.findById("u1")).thenReturn(Optional.of(p))
        assertEquals(HttpStatus.FORBIDDEN, controller.getAmbassadorQrCode("u1").statusCode)
        verifyNoInteractions(qrCodeService)
    }

    @Test
    fun `getAmbassadorQrCode unapproved ambassador returns 403`() {
        val p = mock(UserProfile::class.java)
        `when`(p.role).thenReturn(ProfileRoles.AMBASSADOR)
        `when`(p.profileApproved).thenReturn(false)
        `when`(userProfileRepo.findById("u2")).thenReturn(Optional.of(p))
        assertEquals(HttpStatus.FORBIDDEN, controller.getAmbassadorQrCode("u2").statusCode)
        verifyNoInteractions(qrCodeService)
    }

    @Test
    fun `getAmbassadorQrCode approved ambassador returns png`() {
        val p = mock(UserProfile::class.java)
        `when`(p.role).thenReturn(ProfileRoles.AMBASSADOR)
        `when`(p.profileApproved).thenReturn(true)
        `when`(p.name).thenReturn("Sipho")
        `when`(userProfileRepo.findById("u3")).thenReturn(Optional.of(p))
        val img = byteArrayOf(1, 2, 3)
        `when`(qrCodeService.generateQRCodeImage(
            eq("REFER A FRIEND"),
            eq("https://onboarding.izinga.co.za/indivisuals?ref=u3"),
            eq("Sipho"), eq(450), eq(450)
        )).thenReturn(img)

        val response = controller.getAmbassadorQrCode("u3")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MediaType.IMAGE_PNG, response.headers.contentType)
        assertArrayEquals(img, response.body)
    }

    @Test
    fun `getAmbassadorQrCode null name falls back to userId`() {
        val p = mock(UserProfile::class.java)
        `when`(p.role).thenReturn(ProfileRoles.AMBASSADOR)
        `when`(p.profileApproved).thenReturn(true)
        `when`(p.name).thenReturn(null)
        `when`(userProfileRepo.findById("u4")).thenReturn(Optional.of(p))
        val img = byteArrayOf(7, 8, 9)
        `when`(qrCodeService.generateQRCodeImage(any(), any(), eq("u4"), anyInt(), anyInt())).thenReturn(img)

        val response = controller.getAmbassadorQrCode("u4")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertArrayEquals(img, response.body)
    }

    @Test
    fun `getAmbassadorQrCode qr service throws returns 500`() {
        val p = mock(UserProfile::class.java)
        `when`(p.role).thenReturn(ProfileRoles.AMBASSADOR)
        `when`(p.profileApproved).thenReturn(true)
        `when`(p.name).thenReturn("Jane")
        `when`(userProfileRepo.findById("u5")).thenReturn(Optional.of(p))
        `when`(qrCodeService.generateQRCodeImage(any(), any(), any(), anyInt(), anyInt()))
            .thenThrow(RuntimeException("fail"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getAmbassadorQrCode("u5").statusCode)
    }
}
