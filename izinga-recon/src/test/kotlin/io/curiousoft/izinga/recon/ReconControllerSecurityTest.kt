package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutType
import io.curiousoft.izinga.recon.payout.ReferralPartnerPayout
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willDoNothing
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.http.MediaType

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

    // --- Admin-only recon endpoints: non-admin gets 403 -------------------------

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET shopPayoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/recon/shopPayoutBundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET shopPayoutBundle returns 403 for unauthenticated`() {
        mockMvc.perform(get("/recon/shopPayoutBundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET messengerPayoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/recon/messengerPayoutBundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `PATCH shopPayoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(
            patch("/recon/shopPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `PATCH messengerPayoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(
            patch("/recon/messengerPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET payoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/recon/payoutBundle")
            .param("payoutType", "SHOP")
            .param("fromDate", "2025-01-01T00:00:00.000Z")
            .param("toDate", "2025-12-31T00:00:00.000Z"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET payout returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/recon/payout")
            .param("payoutType", "SHOP")
            .param("fromDate", "2025-01-01T00:00:00.000Z")
            .param("toDate", "2025-12-31T00:00:00.000Z")
            .param("toId", "store-001"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET payoutBundle bundleId payout payoutId returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/recon/payoutBundle/bundle-1/payout/payout-1"))
            .andExpect(status().isForbidden)
    }

    // --- Admin-only recon endpoints: ADMIN role gets 200 -------------------------

    /**
     * Kotlin-safe any() helper: ArgumentMatchers.any() returns null at runtime,
     * which triggers a Kotlin null-safety check on non-nullable params before the
     * mock can intercept the call. The unchecked cast to T suppresses that check so
     * Mockito sees its own null sentinel and matches any argument correctly.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET shopPayoutBundle returns 200 for ADMIN`() {
        val bundle = PayoutBundle(PayoutType.SHOP, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForShops()).willReturn(bundle)

        mockMvc.perform(get("/recon/shopPayoutBundle"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `PATCH shopPayoutBundle returns 200 for ADMIN`() {
        // updatePayoutStatus returns Unit; the mock does nothing by default — no stub needed.
        mockMvc.perform(
            patch("/recon/shopPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET messengerPayoutBundle returns 200 for ADMIN`() {
        val bundle = PayoutBundle(PayoutType.MESSENGER, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForMessenger()).willReturn(bundle)

        mockMvc.perform(get("/recon/messengerPayoutBundle"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `PATCH messengerPayoutBundle returns 200 for ADMIN`() {
        // updatePayoutStatus returns Unit; the mock does nothing by default — no stub needed.
        mockMvc.perform(
            patch("/recon/messengerPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET payoutBundle returns 200 for ADMIN`() {
        given(reconService.getAllPayoutBundles(anyKt(), anyKt(), anyKt()))
            .willReturn(emptyList())

        mockMvc.perform(get("/recon/payoutBundle")
            .param("payoutType", "SHOP")
            .param("fromDate", "2025-01-01T00:00:00.000Z")
            .param("toDate", "2025-12-31T00:00:00.000Z"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET payout returns 200 for ADMIN`() {
        given(reconService.getAllPayouts(anyKt(), anyKt(), anyKt(), anyKt()))
            .willReturn(emptyList())

        mockMvc.perform(get("/recon/payout")
            .param("payoutType", "SHOP")
            .param("fromDate", "2025-01-01T00:00:00.000Z")
            .param("toDate", "2025-12-31T00:00:00.000Z")
            .param("toId", "store-001"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET payoutBundle bundleId payout payoutId returns 200 for ADMIN`() {
        given(reconService.findPayout("bundle-1", "payout-1")).willReturn(null)

        mockMvc.perform(get("/recon/payoutBundle/bundle-1/payout/payout-1"))
            .andExpect(status().isOk)
    }

    // --- NOTE-01: unauthenticated 403 for messengerPayoutBundle ------------------

    @Test
    fun `GET messengerPayoutBundle returns 403 for unauthenticated`() {
        mockMvc.perform(get("/recon/messengerPayoutBundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `PATCH messengerPayoutBundle returns 403 for unauthenticated`() {
        mockMvc.perform(
            patch("/recon/messengerPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isForbidden)
    }

    // --- RECON-PHASE2: ambassadorPayoutBundle endpoints --------------------------

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET ambassadorPayoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/recon/ambassadorPayoutBundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET ambassadorPayoutBundle returns 403 for unauthenticated`() {
        mockMvc.perform(get("/recon/ambassadorPayoutBundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET ambassadorPayoutBundle returns 200 for ADMIN`() {
        val bundle = PayoutBundle(PayoutType.AMBASSADOR, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForAmbassadors()).willReturn(bundle)

        mockMvc.perform(get("/recon/ambassadorPayoutBundle"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `PATCH ambassadorPayoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(
            patch("/recon/ambassadorPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `PATCH ambassadorPayoutBundle returns 200 for ADMIN`() {
        mockMvc.perform(
            patch("/recon/ambassadorPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isOk)
    }

    // --- RECON-PHASE2: referralPartnerPayoutBundle endpoints ---------------------

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET referralPartnerPayoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/recon/referralPartnerPayoutBundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET referralPartnerPayoutBundle returns 403 for unauthenticated`() {
        mockMvc.perform(get("/recon/referralPartnerPayoutBundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET referralPartnerPayoutBundle returns 200 for ADMIN`() {
        val bundle = PayoutBundle(PayoutType.REFERRAL_PARTNER, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForReferralPartners()).willReturn(bundle)

        mockMvc.perform(get("/recon/referralPartnerPayoutBundle"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `PATCH referralPartnerPayoutBundle returns 403 for non-ADMIN`() {
        mockMvc.perform(
            patch("/recon/referralPartnerPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `PATCH referralPartnerPayoutBundle returns 200 for ADMIN`() {
        mockMvc.perform(
            patch("/recon/referralPartnerPayoutBundle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bundleId":"b1","payoutItemResults":[]}""")
        ).andExpect(status().isOk)
    }
}
