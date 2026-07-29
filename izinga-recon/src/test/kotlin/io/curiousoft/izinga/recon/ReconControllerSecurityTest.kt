package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.ReferralPartnerPayout
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * RP-010 BLOCKING-1 fix: Spring Security integration tests for ReconController.
 *
 * Uses @WebMvcTest (real Spring MVC + Security context, see WebMvcTestConfiguration)
 * to prove that @PreAuthorize("hasRole('REFERRAL_PARTNER')") on
 * GET /recon/referral-partner/me/payouts is enforced at the AOP proxy level.
 *
 * Direct controller instantiation in ReconControllerReferralPartnerPayoutsTest bypasses
 * the Spring Security AOP proxy entirely -- these tests do not.
 *
 * NOTE: @WithMockUser injects a pre-built Authentication object and completely bypasses
 * FirebaseJwtAuthenticationConverter. These tests verify @PreAuthorize SpEL enforcement
 * in isolation — they do NOT verify the real JWT-to-Authentication conversion path.
 * See FirebaseJwtAuthenticationConverterTest (izinga-ordermanager) for that coverage.
 *
 * Exact stub values are used for the 200 path (not matchers) to avoid the Kotlin
 * non-null NullPointerException that Mockito any() triggers on non-nullable params.
 * The stub matches exactly what the controller will call: partnerId from the principal
 * name and the default PageRequest (page=0, size=20, sort by modifiedDate DESC).
 */
@WebMvcTest(controllers = [ReconController::class])
class ReconControllerSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var reconService: ReconService

    @MockBean
    lateinit var referralPartnerPayoutRepository: ReferralPartnerPayoutRepository

    // --- GET /recon/referral-partner/me/payouts ----------------------------------

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `getReferralPartnerPayouts returns 403 for non-REFERRAL_PARTNER role`() {
        mockMvc.perform(get("/recon/referral-partner/me/payouts"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "rp-001", roles = ["REFERRAL_PARTNER"])
    fun `getReferralPartnerPayouts returns 200 for REFERRAL_PARTNER role`() {
        // Exact stub: controller uses principal.name ("rp-001") and default page params
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modifiedDate"))
        given(referralPartnerPayoutRepository.findAllByToId("rp-001", pageable))
            .willReturn(PageImpl(emptyList<ReferralPartnerPayout>()))

        mockMvc.perform(get("/recon/referral-partner/me/payouts"))
            .andExpect(status().isOk)
    }
}
