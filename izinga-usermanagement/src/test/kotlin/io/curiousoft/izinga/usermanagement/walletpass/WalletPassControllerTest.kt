package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.commons.model.DeviceType
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner::class)
@SpringBootTest
@AutoConfigureMockMvc
class WalletPassControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun createApplePass() {
        mockMvc.perform(
            get("/walletpass/test-user-id/${DeviceType.APPLE}")
        )
            .andExpect(status().isNotFound) // Will be 404 since user doesn't exist
    }

    @Test
    fun createAndroidPass() {
        mockMvc.perform(
            get("/walletpass/test-user-id/${DeviceType.ANDROID}")
        )
            .andExpect(status().isNotFound) // Will be 404 since user doesn't exist
    }
}
