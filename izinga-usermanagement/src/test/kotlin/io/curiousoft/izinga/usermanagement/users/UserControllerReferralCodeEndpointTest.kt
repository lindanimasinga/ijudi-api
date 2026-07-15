package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.util.Optional

/**
 * RP-002/RP-003: Tests for POST /user/{userId}/referral-code endpoint.
 * Unblocks izinga-onboarding RP-002 which calls this after ICA acceptance.
 */
@ExtendWith(MockitoExtension::class)
class UserControllerReferralCodeEndpointTest {

    @Mock lateinit var profileService: UserProfileService
    @Mock lateinit var userProfileRepo: UserProfileRepo
    @Mock lateinit var qrCodeService: QRCodeService
    @Mock lateinit var ambassadorPayoutRepo: AmbassadorPayoutRepository
    @Mock lateinit var referralCodeService: ReferralCodeService

    private lateinit var controller: UserController

    @BeforeEach
    fun setUp() {
        controller = UserController(profileService, userProfileRepo, qrCodeService, ambassadorPayoutRepo, referralCodeService)
    }

    @Test
    fun `assignReferralCode returns 200 with updated profile for valid REFERRAL_PARTNER`() {
        val profile = referralPartner("rp-001")
        val updated = referralPartner("rp-001").also { it.referralCode = "ABC12345" }

        `when`(userProfileRepo.findById("rp-001")).thenReturn(Optional.of(profile))
        `when`(referralCodeService.assignReferralCode(profile)).thenReturn(updated)

        val response = controller.assignReferralCode("rp-001")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ABC12345", response.body?.referralCode)
        verify(referralCodeService).assignReferralCode(profile)
    }

    @Test
    fun `assignReferralCode is idempotent - returns existing code if already assigned`() {
        val profile = referralPartner("rp-002").also { it.referralCode = "EXISTING1" }
        `when`(userProfileRepo.findById("rp-002")).thenReturn(Optional.of(profile))
        `when`(referralCodeService.assignReferralCode(profile)).thenReturn(profile) // no-op returns same

        val response = controller.assignReferralCode("rp-002")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("EXISTING1", response.body?.referralCode)
        verify(referralCodeService).assignReferralCode(profile)
    }

    @Test
    fun `assignReferralCode returns 404 when user not found`() {
        `when`(userProfileRepo.findById("unknown")).thenReturn(Optional.empty())

        val response = controller.assignReferralCode("unknown")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verifyNoInteractions(referralCodeService)
    }

    @Test
    fun `assignReferralCode returns 400 when user is not a REFERRAL_PARTNER`() {
        val profile = UserProfile("Regular User", UserProfile.SignUpReason.BUY,
            "1 Main St", "https://img.test/u.png", "+27831234567", ProfileRoles.CUSTOMER)
        profile.id = "customer-001"

        `when`(userProfileRepo.findById("customer-001")).thenReturn(Optional.of(profile))

        val response = controller.assignReferralCode("customer-001")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verifyNoInteractions(referralCodeService)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun referralPartner(id: String): UserProfile {
        val p = UserProfile("Referral Partner", UserProfile.SignUpReason.BUY,
            "1 Partner St", "https://img.test/rp.png", "+27820000001", ProfileRoles.REFERRAL_PARTNER)
        p.id = id
        return p
    }
}
