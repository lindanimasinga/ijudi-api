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
        service = UserProfileService(userProfileRepo, eventPublisher, userConfigService, icaAcceptanceLogRepo, referralCodeService)
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
    fun findUserByPhone_customerAudience_thirdPartyPhone_serviceIsUngated() {
        // AC-04 (ADR-026 Phase 2): gate moved to UserMcpController — service must NOT throw.
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")
        Mockito.`when`(userProfileRepo.findByMobileNumber(Mockito.anyString())).thenReturn(null)

        // Must NOT throw CallerIdentityViolationException from the service layer
        try {
            service.findUserByPhone("+27811111111")
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError(
                "AC-04: UserProfileService.findUserByPhone must NOT enforce identity gate after Phase 2 migration. " +
                "Gate belongs in UserMcpController.", e)
        }
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
    fun create_customerAudience_thirdPartyPhone_serviceIsUngated() {
        // AC-04 (ADR-026 Phase 2): gate moved to UserMcpController — service must NOT throw.
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")
        Mockito.`when`(userProfileRepo.findByMobileNumber(Mockito.anyString())).thenReturn(null)

        val profile = UserProfile(
            "Other Name",
            UserProfile.SignUpReason.BUY,
            "Address",
            "https://image.url",
            "+27811111111",
            ProfileRoles.CUSTOMER,
        )

        // Must NOT throw CallerIdentityViolationException from the service layer
        try {
            service.create(profile)
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError(
                "AC-04: UserProfileService.create must NOT enforce identity gate after Phase 2 migration. " +
                "Gate belongs in UserMcpController.", e)
        } catch (_: Exception) {
            // Any other exception (validation, etc.) is fine — identity gate passed
        }
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

    // ---- AMD-01 (REQ-A10): ADMIN audience passes identity validators on MCP path ----

    /**
     * AMD-01 (REQ-A10): find_user_by_phone with ADMIN audience must pass the identity gate
     * and look up a third-party phone without throwing CallerIdentityViolationException.
     */
    @Test
    fun `findUserByPhone adminAudience thirdPartyPhone passes`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")
        // No callerPhone set — ADMIN bypasses phone matching
        Mockito.`when`(userProfileRepo.findByMobileNumber(Mockito.anyString())).thenReturn(null)

        // Must not throw CallerIdentityViolationException
        val result = service.findUserByPhone("+27811111111")
        // Returns null because repo returns null — that is fine; identity gate passed
        assertNull(result)
    }

    /**
     * AMD-01 (REQ-A10): create_user with ADMIN audience must pass the identity gate even when
     * the phone differs from the caller's.  The test verifies that no CallerIdentityViolationException
     * is thrown; any subsequent validation error (missing field, duplicate check, etc.) is acceptable.
     */
    @Test
    fun `create adminAudience thirdPartyPhone passesIdentityGate`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")
        Mockito.`when`(userProfileRepo.findByMobileNumber(Mockito.anyString())).thenReturn(null)

        val profile = UserProfile(
            "Other Driver",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "Address",
            "https://image.url",
            "+27811111111",
            ProfileRoles.CUSTOMER,
        )

        try {
            service.create(profile)
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError("ADMIN audience must not trigger CallerIdentityViolationException", e)
        } catch (_: Exception) {
            // Any other exception (validation, repo, etc.) is fine — identity gate passed
        }
    }

    /**
     * AMD-01 (REQ-A10): get_missing_fields_by_phone with ADMIN audience must pass the identity gate
     * for a third-party phone.  getAllMissingFields delegates to findUserByPhone which applies the
     * validateCallerPhone guard — ADMIN early return must fire before any rejection.
     */
    @Test
    fun `getAllMissingFields adminAudience thirdPartyPhone passes`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")
        Mockito.`when`(userProfileRepo.findByMobileNumber(Mockito.anyString())).thenReturn(null)

        // getAllMissingFields returns empty list when user is null (not found), must not throw
        try {
            service.getAllMissingFields("+27811111111")
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError("ADMIN audience must not trigger CallerIdentityViolationException", e)
        } catch (_: Exception) {
            // Other exceptions (NullPointerException from null user, etc.) are acceptable
        }
    }

    // ---- SEC-MCP-BUG-01 / FIX-05: tokenless MCP guard is FIRST ----

    /**
     * AC-04 (ADR-026 Phase 2): Tokenless MCP call to the SERVICE layer for find_user_by_phone
     * must NOT throw IDENTITY_REFUSAL — the tokenless gate is now in UserMcpController.
     * With a short phone the service returns null via the short-phone guard.
     */
    @Test
    fun findUserByPhone_mcpTokenless_shortPhone_serviceReturnsNullNoGate() {
        CallerIdentityContext.setMcpRequest(true)
        // No audience — tokenless, but service has no gate any more

        try {
            val result = service.findUserByPhone("123")
            // Short phone → null; no IDENTITY_REFUSAL thrown from service
            assertNull(result, "Short phone must return null (not throw) from service after Phase 2")
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError(
                "AC-04: Service must NOT enforce tokenless gate after Phase 2. " +
                "Gate belongs in UserMcpController.", e)
        }
        Mockito.verify(userProfileRepo, Mockito.never()).findByMobileNumber(Mockito.anyString())
    }

    /**
     * AC-04 (ADR-026 Phase 2): Tokenless MCP call to the SERVICE layer for create_user must
     * NOT throw IDENTITY_REFUSAL — the tokenless gate is now in UserMcpController.
     * With a null phone the service throws its own validation error (not IDENTITY_REFUSAL).
     */
    @Test
    fun create_mcpTokenless_nullPhone_serviceThrowsValidationNotIdentityRefusal() {
        CallerIdentityContext.setMcpRequest(true)
        // No audience — tokenless, but service has no gate any more
        val profile = UserProfile(
            "Name",
            UserProfile.SignUpReason.BUY,
            "Address",
            "https://image.url",
            null,  // null phone
            ProfileRoles.CUSTOMER,
        )

        // Must NOT throw CallerIdentityViolationException (gate is in controller, not service)
        try {
            service.create(profile)
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError(
                "AC-04: Service must NOT enforce tokenless gate after Phase 2. " +
                "Gate belongs in UserMcpController.", e)
        } catch (_: Exception) {
            // Any other exception (validation, IllegalArgumentException, etc.) is expected
        }
    }
}
