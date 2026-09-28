package io.curiousoft.izinga.documentmanagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

/**
 * Unit tests for DocumentInfoService that use MockRestServiceServer so they run
 * without a live OpenAI API key.  The companion @Disabled class
 * (DocumentInfoServiceTest) covers live smoke tests separately.
 */
class DocumentInfoServiceUnitTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var service: DocumentInfoService

    @BeforeEach
    fun setUp() {
        restTemplate = RestTemplate()
        mockServer = MockRestServiceServer.createServer(restTemplate)
        service = DocumentInfoService(restTemplate, "test-api-key")
    }

    @Test
    fun `createImage sends model field as dall-e-2 in request body`() {
        val fakeResponse = """{"data":[{"url":"https://example.com/image1.png"},{"url":"https://example.com/image2.png"}]}"""

        mockServer.expect(requestTo("https://api.openai.com/v1/images/generations"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("dall-e-2"))
            .andExpect(jsonPath("$.prompt").value("a red apple"))
            .andExpect(jsonPath("$.n").value(2))
            .andExpect(jsonPath("$.size").value("256x256"))
            .andRespond(withSuccess(fakeResponse, MediaType.APPLICATION_JSON))

        val urls = service.createImage("a red apple", 2, "256x256")

        mockServer.verify()
        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://example.com/image1.png"))
        assertTrue(urls.contains("https://example.com/image2.png"))
    }

    @Test
    fun `createImage returns empty list when response data is null`() {
        val fakeResponse = """{}"""

        mockServer.expect(requestTo("https://api.openai.com/v1/images/generations"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("dall-e-2"))
            .andRespond(withSuccess(fakeResponse, MediaType.APPLICATION_JSON))

        val urls = service.createImage("a blue sky", 1, "1024x1024")

        mockServer.verify()
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `createImage uses default n=1 and size 1024x1024 when not specified`() {
        val fakeResponse = """{"data":[{"url":"https://example.com/default.png"}]}"""

        mockServer.expect(requestTo("https://api.openai.com/v1/images/generations"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("dall-e-2"))
            .andExpect(jsonPath("$.n").value(1))
            .andExpect(jsonPath("$.size").value("1024x1024"))
            .andRespond(withSuccess(fakeResponse, MediaType.APPLICATION_JSON))

        val urls = service.createImage("a mountain landscape")

        mockServer.verify()
        assertEquals(1, urls.size)
        assertEquals("https://example.com/default.png", urls[0])
    }
}
