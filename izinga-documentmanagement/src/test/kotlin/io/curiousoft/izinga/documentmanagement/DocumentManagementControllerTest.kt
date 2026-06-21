package io.curiousoft.izinga.documentmanagement

import io.curiousoft.izinga.documentmanagement.type.DocTypesEnum
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@RunWith(SpringRunner::class)
@SpringBootTest
@AutoConfigureMockMvc
class DocumentManagementControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun uploadFileWithoutMetadata() {
        val file = MockMultipartFile(
            "file",
            "test.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "test image content".toByteArray()
        )

        mockMvc.perform(
            multipart("/document")
                .file(file)
                .param("metadata", "false")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fileName").exists())
            .andExpect(jsonPath("$.url").exists())
    }

    @Test
    fun uploadFileWithMetadata() {
        val file = MockMultipartFile(
            "file",
            "test.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "test image content".toByteArray()
        )

        mockMvc.perform(
            multipart("/document")
                .file(file)
                .param("metadata", "true")
                .param("docType", DocTypesEnum.DRIVERS_LICENCE.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fileName").exists())
            .andExpect(jsonPath("$.url").exists())
    }

    @Test
    fun deleteFile() {
        mockMvc.perform(
            delete("/document")
                .param("fileName", "test_file.jpg")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").exists())
    }
}
