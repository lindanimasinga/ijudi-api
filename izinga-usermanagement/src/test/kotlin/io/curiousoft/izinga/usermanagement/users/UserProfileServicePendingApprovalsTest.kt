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
 * Tests for [UserProfileService.pendingAproval].
 *
 * The method applies two exclusion layers:
 *  1. DB layer — findByProfileApprovedAndRoleNot(false, CUSTOMER) removes docs where
 *     role == "CUSTOMER".
 *  2. In-memory filter — `.filter { it.role != null }` removes docs where the role field
 *     is entirely absent from MongoDB. MongoDB's $ne does NOT exclude missing-field documents
 *     (a missing value is "not equal to CUSTOMER" and passes through). These are incomplete
 *     OTP-placeholder signups that never selected a service type.
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

    @Test
    fun `pendingAproval returns unapproved STORE profiles`() {
        val store = unapprovedProfile(ProfileRoles.STORE)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(store))

        val result = profileService.pendingAproval()

        assertEquals(listOf(store), result)
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
    }

    @Test
    fun `pendingAproval returns unapproved MESSENGER_ADMIN profiles`() {
        val messengerAdmin = unapprovedProfile(ProfileRoles.MESSENGER_ADMIN)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(messengerAdmin))

        val result = profileService.pendingAproval()

        assertEquals(listOf(messengerAdmin), result)
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
    }

    @Test
    fun `pendingAproval returns unapproved REFERRAL_PARTNER profiles`() {
        val partner = unapprovedProfile(ProfileRoles.REFERRAL_PARTNER)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(partner))

        val result = profileService.pendingAproval()

        assertEquals(listOf(partner), result)
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
    }

    // -------------------------------------------------------------------------
    // Null-role exclusion (the follow-up fix for missing-field documents)
    // -------------------------------------------------------------------------

    @Test
    fun `pendingAproval excludes profiles with null role`() {
        val nullRoleProfile = unapprovedProfile(null)
        // The repo returns the null-role doc (MongoDB $ne passes it through)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(nullRoleProfile))

        val result = profileService.pendingAproval()

        assertTrue(result.isEmpty(),
            "A profile with role=null must be excluded from pending approvals")
        verify(profileRepo).findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER)
    }

    @Test
    fun `pendingAproval excludes null role profile when mixed with valid service-provider profiles`() {
        val nullRoleProfile = unapprovedProfile(null)
        val messenger = unapprovedProfile(ProfileRoles.MESSENGER)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(nullRoleProfile, messenger))

        val result = profileService.pendingAproval()

        assertEquals(1, result.size)
        assertEquals(ProfileRoles.MESSENGER, result[0].role)
    }

    @Test
    fun `pendingAproval result contains no null-role entries`() {
        val nullRoleProfile = unapprovedProfile(null)
        val storeAdmin = unapprovedProfile(ProfileRoles.STORE_ADMIN)
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(listOf(nullRoleProfile, storeAdmin))

        val result = profileService.pendingAproval()

        assertFalse(result.any { it.role == null },
            "No null-role profiles must appear in pending approvals")
        assertTrue(result.any { it.role == ProfileRoles.STORE_ADMIN })
    }

    // -------------------------------------------------------------------------
    // Bug regression: CUSTOMER-role still excluded
    // -------------------------------------------------------------------------

    @Test
    fun `pendingAproval uses findByProfileApprovedAndRoleNot and does not call findByProfileApproved`() {
        `when`(profileRepo.findByProfileApprovedAndRoleNot(false, ProfileRoles.CUSTOMER))
            .thenReturn(emptyList())

        profileService.pendingAproval()

        // Correct method is called with approved=false and excluding CUSTOMER
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

    private fun unapprovedProfile(role: ProfileRoles?): UserProfile {
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
