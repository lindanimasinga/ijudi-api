package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutType
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for CsvReconController.
 *
 * Verifies that GET /reconcsv/shop-payout-bundle and
 * GET /reconcsv/messenger-payout-bundle require ADMIN role (403 for non-admin).
 *
 * Uses the same WebMvcTestConfiguration (.anyRequest().authenticated())
 * and @EnableMethodSecurity, so @PreAuthorize annotations are enforced.
 */
@WebMvcTest(controllers = [CsvReconController::class])
class CsvReconControllerSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var reconService: ReconService

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET shop-payout-bundle returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/reconcsv/shop-payout-bundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET shop-payout-bundle returns 403 for unauthenticated`() {
        mockMvc.perform(get("/reconcsv/shop-payout-bundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `GET messenger-payout-bundle returns 403 for non-ADMIN`() {
        mockMvc.perform(get("/reconcsv/messenger-payout-bundle"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET messenger-payout-bundle returns 403 for unauthenticated`() {
        mockMvc.perform(get("/reconcsv/messenger-payout-bundle"))
            .andExpect(status().isForbidden)
    }

    // --- ADMIN 200-path tests for CSV endpoints ----------------------------------

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET shop-payout-bundle returns 200 for ADMIN`() {
        val bundle = PayoutBundle(PayoutType.SHOP, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForShops()).willReturn(bundle)
        // updateBundle returns Unit; mock does nothing by default — no stub needed.

        mockMvc.perform(get("/reconcsv/shop-payout-bundle"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET messenger-payout-bundle returns 200 for ADMIN`() {
        val bundle = PayoutBundle(PayoutType.MESSENGER, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForMessenger()).willReturn(bundle)
        // updateBundle returns Unit; mock does nothing by default — no stub needed.

        mockMvc.perform(get("/reconcsv/messenger-payout-bundle"))
            .andExpect(status().isOk)
    }
}
