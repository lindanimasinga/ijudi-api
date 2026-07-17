package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Optional

/**
 * Spring Security integration tests for POST /user/{userId}/referral-code.
 *
 * The existing UserControllerReferralCodeEndpointTest instantiates UserController directly,
 * bypassing the Spring Security AOP proxy entirely. Those tests verify controller logic but
 * cannot exercise the @PreAuthorize annotation.
 *
 * This @WebMvcTest slice wires the full Spring MVC + Security context (via WebMvcTestConfiguration)
 * and proves that:
 *   1. A REFERRAL_PARTNER calling with their own userId passes the SpEL guard → 200.
 *   2. A REFERRAL_PARTNER calling with a DIFFERENT userId is blocked by Spring Security → 403.
 *   3. A CUSTOMER calling with their own userId passes the SpEL guard but is rejected by the
 *      controller's role check — defence in depth → 400.
 *   4. An ADMIN calling with any userId passes the role guard → 200.
 *
 * The self-or-admin SpEL expression is: hasRole('ADMIN') or #userId == authentication.name
 * Spring Security evaluates this against the authenticated principal's username, so the test
 * usernames must match the path variable exactly for the self-access branch.
 */
@WebMvcTest(controllers = [UserController::class])
class UserControllerReferralCodeSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var profileService: UserProfileService

    @MockBean
    lateinit var userProfileRepo: UserProfileRepo

    @MockBean
    lateinit var qrCodeService: QRCodeService

    @MockBean
    lateinit var ambassadorPayoutRepo: AmbassadorPayoutRepository

    @MockBean
    lateinit var referralCodeService: ReferralCodeService

    // --- Test 1: REFERRAL_PARTNER self-access → 200 --------------------------------

    @Test
    @WithMockUser(username = "rp-001", roles = ["REFERRAL_PARTNER"])
    fun `assignReferralCode returns 200 when REFERRAL_PARTNER calls with their own userId`() {
        val profile = referralPartner("rp-001")
        val updated = referralPartner("rp-001").also { it.referralCode = "RPCODE01" }

        given(userProfileRepo.findById("rp-001")).willReturn(Optional.of(profile))
        given(referralCodeService.assignReferralCode(profile)).willReturn(updated)

        mockMvc.perform(post("/user/rp-001/referral-code"))
            .andExpect(status().isOk)
    }

    // --- Test 2: REFERRAL_PARTNER cross-user access → 403 (Spring Security blocks) ---

    @Test
    @WithMockUser(username = "rp-001", roles = ["REFERRAL_PARTNER"])
    fun `assignReferralCode returns 403 when REFERRAL_PARTNER calls with a different userId`() {
        // No stub needed — Spring Security's AOP proxy must reject this before the controller runs.
        mockMvc.perform(post("/user/rp-other/referral-code"))
            .andExpect(status().isForbidden)
    }

    // --- Test 3: CUSTOMER self-access → 400 (SpEL passes, role guard fires) ----------

    @Test
    @WithMockUser(username = "customer-001", roles = ["CUSTOMER"])
    fun `assignReferralCode returns 400 when CUSTOMER calls with their own userId`() {
        // Spring Security lets this through (#userId == authentication.name is satisfied).
        // The controller's role guard then fires because profile.role != REFERRAL_PARTNER.
        val customerProfile = customerProfile("customer-001")
        given(userProfileRepo.findById("customer-001")).willReturn(Optional.of(customerProfile))

        mockMvc.perform(post("/user/customer-001/referral-code"))
            .andExpect(status().isBadRequest)
    }

    // --- Test 4: ADMIN caller with any userId → 200 (existing admin path preserved) --

    @Test
    @WithMockUser(username = "admin-001", roles = ["ADMIN"])
    fun `assignReferralCode returns 200 when ADMIN calls with any userId`() {
        val profile = referralPartner("rp-999")
        val updated = referralPartner("rp-999").also { it.referralCode = "RPCODE99" }

        given(userProfileRepo.findById("rp-999")).willReturn(Optional.of(profile))
        given(referralCodeService.assignReferralCode(profile)).willReturn(updated)

        mockMvc.perform(post("/user/rp-999/referral-code"))
            .andExpect(status().isOk)
    }

    // --- Helpers ------------------------------------------------------------------

    private fun referralPartner(id: String): UserProfile {
        val p = UserProfile(
            "Referral Partner",
            UserProfile.SignUpReason.BUY,
            "1 Partner St",
            "https://img.test/rp.png",
            "+27820000001",
            ProfileRoles.REFERRAL_PARTNER
        )
        p.id = id
        return p
    }

    private fun customerProfile(id: String): UserProfile {
        val p = UserProfile(
            "Regular Customer",
            UserProfile.SignUpReason.BUY,
            "2 Customer Ave",
            "https://img.test/c.png",
            "+27831234567",
            ProfileRoles.CUSTOMER
        )
        p.id = id
        return p
    }
}
