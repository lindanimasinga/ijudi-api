package io.curiousoft.izinga.recon

import org.junit.jupiter.api.Test
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
}
