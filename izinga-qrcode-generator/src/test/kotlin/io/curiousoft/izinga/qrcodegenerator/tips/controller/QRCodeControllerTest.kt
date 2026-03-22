package io.curiousoft.izinga.qrcodegenerator.tips.controller

import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner::class)
@SpringBootTest
@AutoConfigureMockMvc
class QRCodeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var qrCodeService: QRCodeService

    @Test
    fun generateQRCodes() {
        `when`(qrCodeService.generateQRCodeImage(null, "https://tips.izinga.co.za/tip?linkCode=TEST1", "TEST1", 450, 450))
            .thenReturn(ByteArray(100))

        mockMvc.perform(
            get("/generateQRCodes")
                .param("batchSize", "1")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun linkCodeToUser() {
        `when`(qrCodeService.getLinkedUser("TEST123")).thenReturn(null)

        mockMvc.perform(
            post("/linkCode")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"linkCode": "TEST123", "userId": "user123"}""")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun getLinkCodeUser() {
        mockMvc.perform(get("/linkCode/TEST123"))
            .andExpect(status().isOk)
    }
}
