package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.PayoutType
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@RunWith(SpringRunner::class)
@SpringBootTest
@AutoConfigureMockMvc
class ReconControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun shopPayoutBundle() {
        mockMvc.perform(get("/recon/shopPayoutBundle"))
            .andExpect(status().isOk)
    }

    @Test
    fun messengerPayoutBundle() {
        mockMvc.perform(get("/recon/messengerPayoutBundle"))
            .andExpect(status().isOk)
    }

    @Test
    fun getAllPayoutBundles() {
        mockMvc.perform(
            get("/recon/payoutBundle")
                .param("payoutType", PayoutType.SHOP.toString())
                .param("fromDate", "2024-01-01T00:00:00Z")
                .param("toDate", "2024-12-31T23:59:59Z")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun getAllPayouts() {
        mockMvc.perform(
            get("/recon/payout")
                .param("payoutType", PayoutType.SHOP.toString())
                .param("fromDate", "2024-01-01T00:00:00Z")
                .param("toDate", "2024-12-31T23:59:59Z")
                .param("toId", "test-id")
        )
            .andExpect(status().isOk)
    }
}
