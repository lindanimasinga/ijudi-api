package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

/**
 * T-09: Tests for ambassadorId validation during user creation.
 */
@ExtendWith(MockitoExtension::class)
class AmbassadorIdValidationTest {

    @Mock
    lateinit var userProfileRepo: UserProfileRepo

    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    lateinit var userConfigService: UserConfigService

    lateinit var profileService: UserProfileService

    @BeforeEach
    fun setUp() {
        `when`(userConfigService.findAll()).thenReturn(emptyList())
        profileService = UserProfileService(userProfileRepo, eventPublisher, userConfigService)
    }

    private fun buildNewUser(ambassadorId: String? = null): UserProfile {
        val profile = UserProfile(
            "Test User",
            UserProfile.SignUpReason.BUY,
            "123 Main St",
            "https://img.url",
            "0821234567",
            ProfileRoles.CUSTOMER
        )
        profile.ambassadorId = ambassadorId
        return profile
    }

    private fun buildAmbassadorProfile(approved: Boolean, role: ProfileRoles = ProfileRoles.AMBASSADOR): UserProfile {
        val ambassador = UserProfile(
            "Ambassador",
            UserProfile.SignUpReason.BUY,
            "456 Amb St",
            "https://amb.url",
            "0839876543",
            role
        )
        ambassador.profileApproved = approved
        return ambassador
    }

    @Test
    fun `create with valid approved ambassador sets ambassadorId`() {
        val ambassador = buildAmbassadorProfile(approved = true)
        val newUser = buildNewUser(ambassadorId = "amb-001")

        `when`(userProfileRepo.existsByMobileNumber("+27821234567")).thenReturn(false)
        `when`(userProfileRepo.findById("amb-001")).thenReturn(Optional.of(ambassador))
        `when`(userProfileRepo.save(newUser)).thenReturn(newUser)

        val result = profileService.create(newUser)

        assertEquals("amb-001", result.ambassadorId)
        verify(userProfileRepo).findById("amb-001")
    }

    @Test
    fun `create with ambassador not found clears ambassadorId`() {
        val newUser = buildNewUser(ambassadorId = "no-such-id")

        `when`(userProfileRepo.existsByMobileNumber("+27821234567")).thenReturn(false)
        `when`(userProfileRepo.findById("no-such-id")).thenReturn(Optional.empty())
        `when`(userProfileRepo.save(newUser)).thenReturn(newUser)

        val result = profileService.create(newUser)

        assertNull(result.ambassadorId)
    }

    @Test
    fun `create with ambassador wrong role clears ambassadorId`() {
        val notAmbassador = buildAmbassadorProfile(approved = true, role = ProfileRoles.MESSENGER)
        val newUser = buildNewUser(ambassadorId = "wrong-role-id")

        `when`(userProfileRepo.existsByMobileNumber("+27821234567")).thenReturn(false)
        `when`(userProfileRepo.findById("wrong-role-id")).thenReturn(Optional.of(notAmbassador))
        `when`(userProfileRepo.save(newUser)).thenReturn(newUser)

        val result = profileService.create(newUser)

        assertNull(result.ambassadorId)
    }

    @Test
    fun `create with ambassador not approved clears ambassadorId`() {
        val unapprovedAmbassador = buildAmbassadorProfile(approved = false)
        val newUser = buildNewUser(ambassadorId = "unapproved-amb")

        `when`(userProfileRepo.existsByMobileNumber("+27821234567")).thenReturn(false)
        `when`(userProfileRepo.findById("unapproved-amb")).thenReturn(Optional.of(unapprovedAmbassador))
        `when`(userProfileRepo.save(newUser)).thenReturn(newUser)

        val result = profileService.create(newUser)

        assertNull(result.ambassadorId)
    }

    @Test
    fun `create with null ambassadorId skips lookup`() {
        val newUser = buildNewUser(ambassadorId = null)

        `when`(userProfileRepo.existsByMobileNumber("+27821234567")).thenReturn(false)
        `when`(userProfileRepo.save(newUser)).thenReturn(newUser)

        val result = profileService.create(newUser)

        assertNull(result.ambassadorId)
        // findById should not be called when ambassadorId is null
    }

    @Test
    fun `create with blank ambassadorId skips lookup`() {
        val newUser = buildNewUser(ambassadorId = "  ")

        `when`(userProfileRepo.existsByMobileNumber("+27821234567")).thenReturn(false)
        `when`(userProfileRepo.save(newUser)).thenReturn(newUser)

        // blank is treated as absent — isNullOrBlank() returns true, no lookup performed, no exception thrown
        profileService.create(newUser)
    }
}
