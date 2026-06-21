package io.curiousoft.izinga.qrcodegenerator.promocodes

import io.curiousoft.izinga.qrcodegenerator.promocodes.model.PromoType
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner::class)
@SpringBootTest
@AutoConfigureMockMvc
class PromoCodeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun createPromoCodes() {
        mockMvc.perform(
            post("/promocodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    [{
                        "code": "TEST2024",
                        "type": "PERCENTAGE",
                        "value": 10.0,
                        "maxRedemptions": 100
                    }]
                """.trimIndent())
        )
            .andExpect(status().isOk)
    }

    @Test
    fun getPromoCodes() {
        mockMvc.perform(get("/promocodes"))
            .andExpect(status().isOk)
    }

    @Test
    fun getPromoCodesByType() {
        mockMvc.perform(
            get("/promocodes")
                .param("type", PromoType.PERCENTAGE.toString())
        )
            .andExpect(status().isOk)
    }
}
