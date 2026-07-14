package io.curiousoft.izinga.usermanagement.referral

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ReferralCodeServiceTest {

    @Mock
    lateinit var userProfileRepo: UserProfileRepo

    lateinit var service: ReferralCodeService

    @BeforeEach
    fun setUp() {
        service = ReferralCodeService(userProfileRepo)
    }

    // --- helpers ---

    private fun referralPartnerProfile(referralCode: String? = null): UserProfile {
        val p = UserProfile(
            "Test Partner",
            UserProfile.SignUpReason.SELL,
            "1 Partner Street",
            "https://img.url",
            "+27821234567",
            ProfileRoles.REFERRAL_PARTNER
        )
        p.id = "rp-001"
        p.referralCode = referralCode
        return p
    }

    // --- format ---

    @Test
    fun `generated code is 8 characters`() {
        `when`(userProfileRepo.findByReferralCode(anyString())).thenReturn(null)
        val code = service.generateUniqueCode()
        assertEquals(8, code.length)
    }

    @Test
    fun `generated code uses only valid alphabet characters`() {
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toSet()
        `when`(userProfileRepo.findByReferralCode(anyString())).thenReturn(null)
        repeat(20) {
            val code = service.generateUniqueCode()
            assertTrue(code.all { it in alphabet }, "Unexpected char in code: $code")
        }
    }

    @Test
    fun `generated code is uppercase`() {
        `when`(userProfileRepo.findByReferralCode(anyString())).thenReturn(null)
        val code = service.generateUniqueCode()
        assertEquals(code.uppercase(), code)
    }

    // --- assignReferralCode: happy path ---

    @Test
    fun `assignReferralCode sets code on profile and saves`() {
        val profile = referralPartnerProfile(referralCode = null)
        `when`(userProfileRepo.findByReferralCode(anyString())).thenReturn(null)
        `when`(userProfileRepo.save(profile)).thenReturn(profile)

        val result = service.assignReferralCode(profile)

        assertNotNull(result.referralCode)
        assertEquals(8, result.referralCode!!.length)
        verify(userProfileRepo).save(profile)
    }

    // --- assignReferralCode: idempotency ---

    @Test
    fun `assignReferralCode is idempotent — does not regenerate existing code`() {
        val profile = referralPartnerProfile(referralCode = "ABCD1234")

        val result = service.assignReferralCode(profile)

        assertEquals("ABCD1234", result.referralCode)
        // save must NOT be called — code already present
        verify(userProfileRepo, never()).save(any())
        // findByReferralCode must NOT be called — no generation needed
        verify(userProfileRepo, never()).findByReferralCode(anyString())
    }

    // --- assignReferralCode: wrong role ---

    @Test
    fun `assignReferralCode throws for non-REFERRAL_PARTNER role`() {
        val customer = UserProfile(
            "Customer",
            UserProfile.SignUpReason.BUY,
            "2 Street",
            "https://img.url",
            "+27829999999",
            ProfileRoles.CUSTOMER
        )
        assertThrows(IllegalArgumentException::class.java) {
            service.assignReferralCode(customer)
        }
        verify(userProfileRepo, never()).save(any())
    }

    @Test
    fun `assignReferralCode throws for AMBASSADOR role`() {
        val ambassador = UserProfile(
            "Ambassador",
            UserProfile.SignUpReason.BUY,
            "3 Street",
            "https://img.url",
            "+27828888888",
            ProfileRoles.AMBASSADOR
        )
        assertThrows(IllegalArgumentException::class.java) {
            service.assignReferralCode(ambassador)
        }
    }

    // --- assignReferralCode: collision retry ---

    @Test
    fun `assignReferralCode retries on collision and eventually succeeds`() {
        val profile = referralPartnerProfile(referralCode = null)
        val existingHolder = referralPartnerProfile(referralCode = "COLLISION")

        // First 3 calls return a collision, 4th returns null (slot is free)
        `when`(userProfileRepo.findByReferralCode(anyString()))
            .thenReturn(existingHolder)
            .thenReturn(existingHolder)
            .thenReturn(existingHolder)
            .thenReturn(null)

        `when`(userProfileRepo.save(profile)).thenReturn(profile)

        val result = service.assignReferralCode(profile)

        assertNotNull(result.referralCode)
        verify(userProfileRepo, atLeast(4)).findByReferralCode(anyString())
        verify(userProfileRepo).save(profile)
    }

    @Test
    fun `assignReferralCode throws after exhausting MAX_GENERATION_ATTEMPTS`() {
        val profile = referralPartnerProfile(referralCode = null)
        val existingHolder = referralPartnerProfile(referralCode = "ALWAYS")

        // Always collide — triggers exception after MAX_GENERATION_ATTEMPTS
        `when`(userProfileRepo.findByReferralCode(anyString())).thenReturn(existingHolder)

        assertThrows(IllegalStateException::class.java) {
            service.assignReferralCode(profile)
        }
        verify(userProfileRepo, never()).save(any())
    }

    // --- resolveCode ---

    @Test
    fun `resolveCode returns owning profile for valid code`() {
        val profile = referralPartnerProfile(referralCode = "ABC12345")
        `when`(userProfileRepo.findByReferralCode("ABC12345")).thenReturn(profile)

        val result = service.resolveCode("ABC12345")

        assertEquals(profile, result)
        verify(userProfileRepo).findByReferralCode("ABC12345")
    }

    @Test
    fun `resolveCode normalises to uppercase before lookup`() {
        val profile = referralPartnerProfile(referralCode = "ABC12345")
        `when`(userProfileRepo.findByReferralCode("ABC12345")).thenReturn(profile)

        val result = service.resolveCode("abc12345")

        assertEquals(profile, result)
        verify(userProfileRepo).findByReferralCode("ABC12345")
    }

    @Test
    fun `resolveCode strips whitespace before lookup`() {
        val profile = referralPartnerProfile(referralCode = "ABC12345")
        `when`(userProfileRepo.findByReferralCode("ABC12345")).thenReturn(profile)

        val result = service.resolveCode("  ABC12345  ")

        assertEquals(profile, result)
    }

    @Test
    fun `resolveCode returns null for unknown code`() {
        `when`(userProfileRepo.findByReferralCode("NOTFOUND")).thenReturn(null)

        val result = service.resolveCode("NOTFOUND")

        assertNull(result)
    }

    @Test
    fun `resolveCode returns null for blank code without hitting repo`() {
        val result = service.resolveCode("   ")

        assertNull(result)
        verify(userProfileRepo, never()).findByReferralCode(anyString())
    }
}
