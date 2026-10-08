package io.curiousoft.izinga.payfast.service

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

/**
 * TIER-BILLING-01: Unit tests for [PayFastValidateClientImpl].
 *
 * Covers:
 * - VALID response → PayFastValidateResult.VALID
 * - INVALID response (literal "INVALID") → PayFastValidateResult.INVALID
 * - Non-VALID / unexpected response → PayFastValidateResult.INVALID
 * - PayFast 5xx response → PayFastValidateTransientException
 * - Network / timeout error → PayFastValidateTransientException
 * - Parameter map is URL-encoded and POST'd correctly
 */
class PayFastValidateClientTest {

    private val restTemplate: RestTemplate = mockk()
    private val validateUrl = "https://sandbox.payfast.co.za/eng/query/validate"
    private lateinit var client: PayFastValidateClientImpl

    @BeforeEach
    fun setUp() {
        client = PayFastValidateClientImpl(validateUrl, restTemplate)
    }

    @Test
    fun `validate returns VALID when PayFast responds with 'VALID'`() {
        every { restTemplate.postForObject(validateUrl, any<HttpEntity<String>>(), String::class.java) } returns "VALID"

        val result = client.validate(sampleParams())

        assertEquals(PayFastValidateResult.VALID, result)
    }

    @Test
    fun `validate returns VALID when PayFast responds with 'VALID' padded with whitespace`() {
        every { restTemplate.postForObject(validateUrl, any<HttpEntity<String>>(), String::class.java) } returns "  VALID  \n"

        val result = client.validate(sampleParams())

        assertEquals(PayFastValidateResult.VALID, result)
    }

    @Test
    fun `validate returns INVALID when PayFast responds with 'INVALID'`() {
        every { restTemplate.postForObject(validateUrl, any<HttpEntity<String>>(), String::class.java) } returns "INVALID"

        val result = client.validate(sampleParams())

        assertEquals(PayFastValidateResult.INVALID, result)
    }

    @Test
    fun `validate returns INVALID when PayFast responds with unexpected content`() {
        every { restTemplate.postForObject(validateUrl, any<HttpEntity<String>>(), String::class.java) } returns "UNKNOWN"

        val result = client.validate(sampleParams())

        assertEquals(PayFastValidateResult.INVALID, result)
    }

    @Test
    fun `validate returns INVALID when PayFast responds with null body`() {
        every { restTemplate.postForObject(validateUrl, any<HttpEntity<String>>(), String::class.java) } returns null

        val result = client.validate(sampleParams())

        assertEquals(PayFastValidateResult.INVALID, result)
    }

    @Test
    fun `validate throws PayFastValidateTransientException when PayFast returns 5xx`() {
        every {
            restTemplate.postForObject(validateUrl, any<HttpEntity<String>>(), String::class.java)
        } throws HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)

        val ex = assertThrows(PayFastValidateTransientException::class.java) {
            client.validate(sampleParams())
        }
        assertTrue(ex.message!!.contains("PAYFAST_VALIDATE_CALL_FAILED"),
            "Exception message must contain PAYFAST_VALIDATE_CALL_FAILED marker")
        assertNotNull(ex.cause, "Must preserve original exception as cause")
    }

    @Test
    fun `validate throws PayFastValidateTransientException on network timeout`() {
        every {
            restTemplate.postForObject(validateUrl, any<HttpEntity<String>>(), String::class.java)
        } throws ResourceAccessException("Connection timed out")

        val ex = assertThrows(PayFastValidateTransientException::class.java) {
            client.validate(sampleParams())
        }
        assertTrue(ex.message!!.contains("PAYFAST_VALIDATE_CALL_FAILED"))
        assertNotNull(ex.cause)
    }

    @Test
    fun `validate sends POST body as URL-encoded parameter string including signature`() {
        val capturedEntity = slot<HttpEntity<String>>()
        every {
            restTemplate.postForObject(validateUrl, capture(capturedEntity), String::class.java)
        } returns "VALID"

        val params = mapOf(
            "m_payment_id" to "sub-001",
            "signature" to "abc123",
            "merchant_id" to "16791971"
        )
        client.validate(params)

        val body = capturedEntity.captured.body!!
        // All params must be present in the URL-encoded body
        assertTrue(body.contains("m_payment_id=sub-001"), "Body must contain m_payment_id")
        assertTrue(body.contains("signature=abc123"), "Body must contain signature")
        assertTrue(body.contains("merchant_id=16791971"), "Body must contain merchant_id")
    }

    private fun sampleParams() = mapOf(
        "merchant_id" to "16791971",
        "m_payment_id" to "sub-test",
        "pf_payment_id" to "pf-test",
        "payment_status" to "COMPLETE",
        "signature" to "abc123def456"
    )
}
