package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher

/**
 * Tests for the [UserProfileService.pendingAproval] fix that excludes CUSTOMER-role profiles
 * from the pending approvals list.
 *
 * Prior behaviour: called profileRepo.findByProfileApproved(false) which returned ALL unapproved
 * profiles including CUSTOMER — causing them to appear on the admin Pending Approvals page.
 *
 * Fixed behaviour: calls profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER),
 * which excludes CUSTOMER at the database layer.
 */
@ExtendWith(MockitoExtension::class)
class UserProfileServicePendingApprovalsTest {

    @Mock lateinit var profileRepo: UserProfileRepo
    @Mock lateinit var eventPublisher: ApplicationEventPublisher
    @Mock lateinit var userConfigService: UserConfigService
    @Mock lateinit var icaAcceptanceLogRepo: IcaAcceptanceLogRepo
    @Mock lateinit var referralCodeService: ReferralCodeService

    @InjectMocks
    lateinit var profileService: UserProfileService

    // -------------------------------------------------------------------------
    // Happy path: unapproved service-provider users are returned
    // -------------------------------------------------------------------------

    @Test
    fun `pendingAproval returns unapproved MESSENGER profiles`() {
        val messenger = unapprovedProfile(ProfileRoles.MESSENGER)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(messenger))

        val result = profileService.pendingAproval()

        assertEquals(listOf(messenger), result)
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
    }

    @Test
    fun `pendingAproval returns unapproved STORE_ADMIN profiles`() {
        val storeAdmin = unapprovedProfile(ProfileRoles.STORE_ADMIN)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(storeAdmin))

        val result = profileService.pendingAproval()

        assertEquals(listOf(storeAdmin), result)
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
    }

    @Test
    fun `pendingAproval returns unapproved AMBASSADOR profiles`() {
        val ambassador = unapprovedProfile(ProfileRoles.AMBASSADOR)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(ambassador))

        val result = profileService.pendingAproval()

        assertEquals(listOf(ambassador), result)
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
    }

    // -------------------------------------------------------------------------
    // Bug regression: the OLD findByProfileApproved must no longer be called
    // -------------------------------------------------------------------------

    @Test
    fun `pendingAproval uses findByProfileApprovedAndRoleNot and does not call findByProfileApproved`() {
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(emptyList())

        profileService.pendingAproval()

        // Correct new method is called with approved=false and excluding CUSTOMER
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
        // No other interactions — findByProfileApproved(false) must NOT be called
        verifyNoMoreInteractions(profileRepo)
    }

    @Test
    fun `pendingAproval result does not contain CUSTOMER profiles`() {
        val messenger = unapprovedProfile(ProfileRoles.MESSENGER)
        // Repo returns only non-CUSTOMER profiles (as the fixed query guarantees)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(messenger))

        val result = profileService.pendingAproval()

        assertFalse(result.any { it.role == ProfileRoles.CUSTOMER },
            "CUSTOMER must not appear in pending approvals")
        assertTrue(result.any { it.role == ProfileRoles.MESSENGER })
    }

    // -------------------------------------------------------------------------
    // Empty result: no pending approvals
    // -------------------------------------------------------------------------

    @Test
    fun `pendingAproval returns empty list when no unapproved service-provider profiles exist`() {
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(emptyList())

        val result = profileService.pendingAproval()

        assertTrue(result.isEmpty())
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun unapprovedProfile(role: ProfileRoles): UserProfile {
        val profile = UserProfile(
            "Test User",
            UserProfile.SignUpReason.BUY,
            "address",
            "https://img.url",
            "+27821000001",
            role
        )
        profile.profileApproved = false
        return profile
    }
}
