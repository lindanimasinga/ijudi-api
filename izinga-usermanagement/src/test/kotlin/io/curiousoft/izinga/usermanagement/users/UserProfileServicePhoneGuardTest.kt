package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService
import io.curiousoft.izinga.commons.security.CallerIdentityContext
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher

@ExtendWith(MockitoExtension::class)
class UserProfileServicePhoneGuardTest {

    @Mock
    private lateinit var userProfileRepo: UserProfileRepo

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var userConfigService: UserConfigService

    @Mock
    private lateinit var icaAcceptanceLogRepo: IcaAcceptanceLogRepo

    @Mock
    private lateinit var referralCodeService: ReferralCodeService

    @Mock
    private lateinit var agentIdentityAuditService: AgentIdentityAuditService

    private lateinit var service: UserProfileService

    @BeforeEach
    fun setUp() {
        Mockito.`when`(userConfigService.findAll()).thenReturn(emptyList())
        service = UserProfileService(userProfileRepo, eventPublisher, userConfigService, icaAcceptanceLogRepo, referralCodeService, agentIdentityAuditService)
    }

    @AfterEach
    fun tearDown() {
        CallerIdentityContext.clear()
    }

    @Test
    fun findUserByPhone_returnsNullForShortPhone() {
        val result = service.findUserByPhone("123")
        assertNull(result)
    }

    @Test
    fun create_rejectsShortPhone() {
        val profile = UserProfile(
            "Name",
            UserProfile.SignUpReason.BUY,
            "Address",
            "https://image.url",
            "123",
            ProfileRoles.CUSTOMER,
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.create(profile)
        }
    }

    // ---- ADR-024: caller identity enforcement ----

    @Test
    fun findUserByPhone_customerAudience_ownPhone_passes() {
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")
        // Should not throw — returns null because the repo returns null, not because of an error
        Mockito.`when`(userProfileRepo.findByMobileNumber(Mockito.anyString())).thenReturn(null)
        val result = service.findUserByPhone("+27812815577")
        assertNull(result)
    }

    @Test
    fun findUserByPhone_customerAudience_thirdPartyPhone_throws() {
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")

        val ex = assertThrows(CallerIdentityViolationException::class.java) {
            service.findUserByPhone("+27811111111")
        }
        assertTrue(ex.message!!.startsWith("IDENTITY_REFUSAL:"))
    }

    @Test
    fun findUserByPhone_storeAudience_thirdPartyPhone_noOp() {
        // STORE audience must never be restricted by ADR-024
        CallerIdentityContext.setAudience("STORE")
        CallerIdentityContext.setCallerPhone("+27812815577")
        // Should not throw — repo returns null for both prefixes, that is fine
        Mockito.`when`(userProfileRepo.findByMobileNumber(Mockito.anyString())).thenReturn(null)
        val result = service.findUserByPhone("+27811111111")
        assertNull(result)
    }

    @Test
    fun create_customerAudience_thirdPartyPhone_throws() {
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")

        val profile = UserProfile(
            "Other Name",
            UserProfile.SignUpReason.BUY,
            "Address",
            "https://image.url",
            "+27811111111",
            ProfileRoles.CUSTOMER,
        )

        val ex = assertThrows(CallerIdentityViolationException::class.java) {
            service.create(profile)
        }
        assertTrue(ex.message!!.startsWith("IDENTITY_REFUSAL:"))
    }

    @Test
    fun create_customerAudience_ownPhone_proceedsToValidation() {
        // With own phone the identity check passes; validation then fails because
        // mobileNumber normalizes to +27812815577, but name is required — checks name.
        // This verifies the ADR-024 enforcement does NOT block the legitimate path.
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")
        Mockito.`when`(userProfileRepo.findByMobileNumber(Mockito.anyString())).thenReturn(null)

        val profile = UserProfile(
            "Name",
            UserProfile.SignUpReason.BUY,
            "Address",
            "https://image.url",
            "+27812815577",
            ProfileRoles.CUSTOMER,
        )

        // Identity check passes → reaches createUser flow (repo returns null → save is called or
        // validation path proceeds without CallerIdentityViolationException).
        // The test simply verifies no CallerIdentityViolationException is thrown.
        try {
            service.create(profile)
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError("CallerIdentityViolationException must NOT be thrown for own phone", e)
        } catch (_: Exception) {
            // Any other exception (e.g. NullPointerException from unprepared mocks) is fine —
            // we are only asserting the identity check is not the failure.
        }
    }
}
