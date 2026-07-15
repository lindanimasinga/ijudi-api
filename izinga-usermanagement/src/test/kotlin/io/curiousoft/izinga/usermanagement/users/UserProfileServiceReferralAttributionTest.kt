package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher

/**
 * RP-004a: Unit tests for referral attribution capture at customer registration.
 */
@ExtendWith(MockitoExtension::class)
class UserProfileServiceReferralAttributionTest {

    @Mock lateinit var userProfileRepo: UserProfileRepo
    @Mock lateinit var eventPublisher: ApplicationEventPublisher
    @Mock lateinit var userConfigService: UserConfigService
    @Mock lateinit var icaAcceptanceLogRepo: IcaAcceptanceLogRepo
    @Mock lateinit var referralCodeService: ReferralCodeService

    private lateinit var service: UserProfileService

    @BeforeEach
    fun setUp() {
        `when`(userConfigService.findAll()).thenReturn(emptyList())
        service = UserProfileService(
            userProfileRepo, eventPublisher, userConfigService, icaAcceptanceLogRepo, referralCodeService
        )
    }

    private fun makeCustomer(): UserProfile {
        val p = UserProfile("Test Customer", UserProfile.SignUpReason.BUY, "123 Main St",
            "https://img.test/1.png", "0831234567", ProfileRoles.CUSTOMER)
        p.mobileNumber = "+27831234567"
        return p
    }

    private fun makePartner(id: String): UserProfile {
        val p = UserProfile("Partner", UserProfile.SignUpReason.BUY, "1 Park Ave",
            "https://img.test/p.png", "+27820000001", ProfileRoles.REFERRAL_PARTNER)
        p.id = id
        p.referralCode = "ABC12345"
        return p
    }

    @Test
    fun `create with valid ref code resolves partner and sets referredByPartnerId`() {
        val profile = makeCustomer()
        val partner = makePartner("partner-123")
        val saved = makeCustomer().also { it.id = "new-user-1"; it.referredByPartnerId = "partner-123" }

        `when`(referralCodeService.resolveCode("ABC12345")).thenReturn(partner)
        `when`(userProfileRepo.existsByMobileNumber(anyString())).thenReturn(false)
        `when`(userProfileRepo.save(any())).thenReturn(saved)

        val result = service.create(profile, "ABC12345")

        assertEquals("partner-123", profile.referredByPartnerId)
        verify(referralCodeService).resolveCode("ABC12345")
    }

    @Test
    fun `create with null ref code does not call resolveCode`() {
        val profile = makeCustomer()
        val saved = makeCustomer().also { it.id = "new-user-2" }

        `when`(userProfileRepo.existsByMobileNumber(anyString())).thenReturn(false)
        `when`(userProfileRepo.save(any())).thenReturn(saved)

        service.create(profile, null)

        assertNull(profile.referredByPartnerId)
        verify(referralCodeService, never()).resolveCode(anyString())
    }

    @Test
    fun `create with blank ref code does not call resolveCode`() {
        val profile = makeCustomer()
        val saved = makeCustomer().also { it.id = "new-user-3" }

        `when`(userProfileRepo.existsByMobileNumber(anyString())).thenReturn(false)
        `when`(userProfileRepo.save(any())).thenReturn(saved)

        service.create(profile, "   ")

        assertNull(profile.referredByPartnerId)
        verify(referralCodeService, never()).resolveCode(anyString())
    }

    @Test
    fun `create with unrecognised ref code logs warning and does not set referredByPartnerId`() {
        val profile = makeCustomer()
        val saved = makeCustomer().also { it.id = "new-user-4" }

        `when`(referralCodeService.resolveCode("NOTFOUND")).thenReturn(null)
        `when`(userProfileRepo.existsByMobileNumber(anyString())).thenReturn(false)
        `when`(userProfileRepo.save(any())).thenReturn(saved)

        service.create(profile, "NOTFOUND")

        assertNull(profile.referredByPartnerId)
        verify(referralCodeService).resolveCode("NOTFOUND")
    }

    /**
     * Payload fallback path: controller resolves effectiveRef from profile.referralCode when
     * the `ref` query param is absent, nulls it out, then calls create(profile, effectiveRef).
     * This test covers the service layer behaviour when the code arrives via that path.
     */
    @Test
    fun `create with code passed as effectiveRef from payload fallback resolves partner and sets referredByPartnerId`() {
        val profile = makeCustomer()
        // Controller would have set profile.referralCode = null before calling the service,
        // so here we simulate exactly what arrives: referralCode=null, effectiveRef="PAYLOAD01"
        val partner = makePartner("partner-payload-1")
        val saved = makeCustomer().also { it.id = "new-user-5"; it.referredByPartnerId = "partner-payload-1" }

        `when`(referralCodeService.resolveCode("PAYLOAD01")).thenReturn(partner)
        `when`(userProfileRepo.existsByMobileNumber(anyString())).thenReturn(false)
        `when`(userProfileRepo.save(any())).thenReturn(saved)

        service.create(profile, "PAYLOAD01")

        assertEquals("partner-payload-1", profile.referredByPartnerId)
        verify(referralCodeService).resolveCode("PAYLOAD01")
    }
}
