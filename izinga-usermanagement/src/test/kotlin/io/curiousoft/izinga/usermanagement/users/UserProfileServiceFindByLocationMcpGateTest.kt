package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService
import io.curiousoft.izinga.commons.security.CallerIdentityContext
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

/**
 * AC-04 (ADR-026 Phase 2): verifies that [UserProfileService.findByLocation] is UNGATED after
 * the Phase 2 migration — audience enforcement moved to [UserMcpController.findUsers].
 * Non-MCP (REST/scheduler) calls remain unaffected.
 *
 * JUnit 5 + MockitoExtension (izinga-usermanagement module convention).
 */
@ExtendWith(MockitoExtension::class)
class UserProfileServiceFindByLocationMcpGateTest {

    @Mock private lateinit var userProfileRepo: UserProfileRepo
    @Mock private lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher
    @Mock private lateinit var userConfigService: UserConfigService
    @Mock private lateinit var icaAcceptanceLogRepo: IcaAcceptanceLogRepo
    @Mock private lateinit var referralCodeService: ReferralCodeService
    @Mock private lateinit var agentIdentityAuditService: AgentIdentityAuditService

    private lateinit var service: UserProfileService

    private val lat = -25.7461
    private val lon = 28.1881
    private val range = 0.05
    private val role = ProfileRoles.MESSENGER
    private val storeType = StoreType.FOOD

    @BeforeEach
    fun setUp() {
        `when`(userConfigService.findAll()).thenReturn(emptyList())
        service = UserProfileService(
            userProfileRepo, eventPublisher, userConfigService,
            icaAcceptanceLogRepo, referralCodeService
        )
    }

    @AfterEach
    fun tearDown() {
        CallerIdentityContext.clear()
    }

    // ── AC-04 (Phase 2): service is ungated — all audiences pass through to repo ─────

    @Test
    fun `findByLocation ADMIN audience MCP request passes`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")
        `when`(userProfileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
            role, storeType,
            lat - range, lat + range,
            lon - range, lon + range
        )).thenReturn(emptyList())

        val result = service.findByLocation(role, lat, lon, range, storeType)
        assertNotNull(result)
    }

    @Test
    fun `findByLocation STORE audience MCP request serviceIsUngated`() {
        // AC-04: gate moved to UserMcpController — service must NOT throw for STORE audience
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("STORE")
        `when`(userProfileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
            role, storeType,
            lat - range, lat + range,
            lon - range, lon + range
        )).thenReturn(emptyList())

        try {
            service.findByLocation(role, lat, lon, range, storeType)
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError(
                "AC-04: UserProfileService.findByLocation must NOT enforce audience gate after Phase 2. " +
                "Gate belongs in UserMcpController.", e)
        }
    }

    @Test
    fun `findByLocation CUSTOMER audience MCP request serviceIsUngated`() {
        // AC-04: gate moved to UserMcpController
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("CUSTOMER")
        `when`(userProfileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
            role, storeType,
            lat - range, lat + range,
            lon - range, lon + range
        )).thenReturn(emptyList())

        try {
            service.findByLocation(role, lat, lon, range, storeType)
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError(
                "AC-04: UserProfileService.findByLocation must NOT enforce audience gate after Phase 2.", e)
        }
    }

    @Test
    fun `findByLocation DRIVER audience MCP request serviceIsUngated`() {
        // AC-04: gate moved to UserMcpController
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("DRIVER")
        `when`(userProfileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
            role, storeType,
            lat - range, lat + range,
            lon - range, lon + range
        )).thenReturn(emptyList())

        try {
            service.findByLocation(role, lat, lon, range, storeType)
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError(
                "AC-04: UserProfileService.findByLocation must NOT enforce audience gate after Phase 2.", e)
        }
    }

    @Test
    fun `findByLocation null audience MCP request serviceIsUngated`() {
        // AC-04: gate moved to UserMcpController — null audience must NOT throw from service
        CallerIdentityContext.setMcpRequest(true)
        // audience not set → null
        `when`(userProfileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
            role, storeType,
            lat - range, lat + range,
            lon - range, lon + range
        )).thenReturn(emptyList())

        try {
            service.findByLocation(role, lat, lon, range, storeType)
        } catch (e: CallerIdentityViolationException) {
            throw AssertionError(
                "AC-04: UserProfileService.findByLocation must NOT enforce audience gate after Phase 2.", e)
        }
    }

    // ── REST regression: non-MCP path, null audience → no gate ────────────────

    @Test
    fun `findByLocation non-MCP null audience passes (REST regression)`() {
        // IS_MCP_REQUEST=false → validateMcpAudience is a no-op regardless of audience
        `when`(userProfileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
            role, storeType,
            lat - range, lat + range,
            lon - range, lon + range
        )).thenReturn(emptyList())

        val result = service.findByLocation(role, lat, lon, range, storeType)
        assertNotNull(result)
    }

    @Test
    fun `findByLocation non-MCP CUSTOMER audience passes (REST regression)`() {
        CallerIdentityContext.setAudience("CUSTOMER")
        // IS_MCP_REQUEST=false → gate skipped
        `when`(userProfileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
            role, storeType,
            lat - range, lat + range,
            lon - range, lon + range
        )).thenReturn(emptyList())

        val result = service.findByLocation(role, lat, lon, range, storeType)
        assertNotNull(result)
    }
}
