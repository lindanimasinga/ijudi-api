package io.curiousoft.izinga.usermanagement.referral

import io.curiousoft.izinga.commons.referral.ReferralCommissionStatus
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
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
import java.math.BigDecimal
import java.util.Date

/**
 * RP-010 BLOCKING-1 fix: Spring Security integration tests for ReferralPartnerController.
 *
 * Uses @WebMvcTest (real Spring MVC + Security context, see WebMvcTestConfiguration)
 * to prove that @PreAuthorize("hasRole('REFERRAL_PARTNER')") is enforced at the AOP
 * proxy level. Direct controller instantiation in ReferralPartnerControllerTest bypasses
 * the Spring Security AOP proxy entirely -- these tests do not.
 *
 * Pattern: one 403-test per endpoint (non-REFERRAL_PARTNER caller) + one 200-test per
 * endpoint (REFERRAL_PARTNER caller). Three endpoints = 6 tests.
 *
 * Exact stub values are used for the 200 paths (not Mockito matchers) to avoid the
 * NullPointerException that Mockito's any() causes on Kotlin non-null parameters.
 * The stubs match exactly what the controller constructs from principal.name ("rp-001")
 * and the default request parameters.
 */
@WebMvcTest(controllers = [ReferralPartnerController::class])
class ReferralPartnerControllerSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var dashboardService: ReferralPartnerDashboardService

    // --- GET /referral-partner/me/summary ----------------------------------------

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `getSummary returns 403 for non-REFERRAL_PARTNER role`() {
        mockMvc.perform(get("/referral-partner/me/summary"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "rp-001", roles = ["REFERRAL_PARTNER"])
    fun `getSummary returns 200 for REFERRAL_PARTNER role`() {
        given(dashboardService.getSummary("rp-001")).willReturn(summaryResponse("rp-001"))

        mockMvc.perform(get("/referral-partner/me/summary"))
            .andExpect(status().isOk)
    }

    // --- GET /referral-partner/me/referrals --------------------------------------

    @Test
    @WithMockUser(roles = ["STORE_ADMIN"])
    fun `getReferrals returns 403 for non-REFERRAL_PARTNER role`() {
        mockMvc.perform(get("/referral-partner/me/referrals"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "rp-001", roles = ["REFERRAL_PARTNER"])
    fun `getReferrals returns 200 for REFERRAL_PARTNER role`() {
        // Exact stub: controller uses principal.name and default page=0, size=20, sort by createdDate DESC
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"))
        given(dashboardService.getReferrals("rp-001", pageable))
            .willReturn(PageImpl(emptyList<ReferralItem>()))

        mockMvc.perform(get("/referral-partner/me/referrals"))
            .andExpect(status().isOk)
    }

    // --- GET /referral-partner/me/commissions ------------------------------------

    @Test
    @WithMockUser(roles = ["MESSENGER"])
    fun `getCommissions returns 403 for non-REFERRAL_PARTNER role`() {
        mockMvc.perform(get("/referral-partner/me/commissions"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "rp-001", roles = ["REFERRAL_PARTNER"])
    fun `getCommissions returns 200 for REFERRAL_PARTNER role`() {
        given(dashboardService.getCommissions("rp-001")).willReturn(commissionSummary())

        mockMvc.perform(get("/referral-partner/me/commissions"))
            .andExpect(status().isOk)
    }

    // --- Helpers -----------------------------------------------------------------

    private fun summaryResponse(partnerId: String) = ReferralPartnerSummary(
        partnerId = partnerId,
        referralCode = "CODE1",
        referralCounts = ReferralCounts(foodCustomers = 0, furnitureCustomers = 0, storePartners = 0),
        conversionCounts = ConversionCounts(foodCustomers = 0, furnitureCustomers = 0, storePartnersStage1 = 0, storePartnersStage2 = 0)
    )

    private fun commissionSummary() = CommissionSummary(
        totals = CommissionTotals(pending = BigDecimal.ZERO, paid = BigDecimal.ZERO),
        lineItems = listOf(
            CommissionLineItem(
                commissionType = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL,
                amount = BigDecimal("15.00"),
                status = ReferralCommissionStatus.PENDING,
                triggerReferenceId = "c-1",
                createdAt = Date()
            )
        )
    )
}
