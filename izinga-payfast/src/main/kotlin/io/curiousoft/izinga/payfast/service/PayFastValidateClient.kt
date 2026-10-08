package io.curiousoft.izinga.payfast.service

import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Result of a PayFast server-to-server validate call. */
enum class PayFastValidateResult { VALID, INVALID }

/**
 * Posts the ITN parameters back to PayFast's `/eng/query/validate` endpoint and returns
 * whether PayFast confirms the payment as real.
 *
 * SEC-TB01-01-D (CRITICAL): No bypass path exists in production code. This interface is
 * always wired into [PayFastItnHandler]; the production class contains no conditional skip
 * logic. In test profiles, a `@Profile("test")` mock bean may substitute this implementation.
 *
 * Behaviour contract:
 * - Returns [PayFastValidateResult.VALID]   → PayFast confirmed the payment. Proceed.
 * - Returns [PayFastValidateResult.INVALID] → PayFast denied the payment (permanent). Return HTTP 200.
 * - Throws  [PayFastValidateTransientException] → network/5xx (transient). Caller returns HTTP 500
 *   so PayFast retries. (SEC-TB01-01-C)
 */
interface PayFastValidateClient {
    /**
     * Validates an ITN with PayFast's server-to-server endpoint.
     *
     * @param params the full ITN parameter map (including "signature").
     * @return [PayFastValidateResult.VALID] or [PayFastValidateResult.INVALID].
     * @throws PayFastValidateTransientException on timeout, network error, or PayFast 5xx.
     *   The caller MUST allow this to propagate so HTTP 500 is returned (SEC-TB01-01-C).
     */
    fun validate(params: Map<String, String>): PayFastValidateResult
}

/**
 * Production RestTemplate-based implementation.
 *
 * Reconstructs the URL-encoded ITN parameter string from [params] and POSTs it to
 * [validateUrl]. The RestTemplate is pre-configured with the operator-tunable timeout
 * from `payfast.subscription.validateTimeoutSeconds`.
 */
class PayFastValidateClientImpl(
    private val validateUrl: String,
    private val restTemplate: RestTemplate
) : PayFastValidateClient {

    private val log = LoggerFactory.getLogger(PayFastValidateClientImpl::class.java)

    override fun validate(params: Map<String, String>): PayFastValidateResult {
        // Reconstruct the URL-encoded body that PayFast expects back.
        val body = params.entries.joinToString("&") {
            "${urlEncode(it.key)}=${urlEncode(it.value)}"
        }
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val entity = HttpEntity(body, headers)

        return try {
            val response = restTemplate.postForObject(validateUrl, entity, String::class.java)
            if (response?.trim() == "VALID") {
                log.debug("PAYFAST_VALIDATE: VALID for m_payment_id={}", params["m_payment_id"])
                PayFastValidateResult.VALID
            } else {
                log.warn("PAYFAST_VALIDATE_INVALID: response='{}' m_payment_id={}",
                    response?.trim(), params["m_payment_id"])
                PayFastValidateResult.INVALID
            }
        } catch (e: HttpServerErrorException) {
            // PayFast validate endpoint returned 5xx — transient.
            throw PayFastValidateTransientException(
                "PAYFAST_VALIDATE_CALL_FAILED: PayFast returned ${e.statusCode} " +
                    "m_payment_id=${params["m_payment_id"]}", e)
        } catch (e: ResourceAccessException) {
            // Network timeout or connection refused — transient.
            throw PayFastValidateTransientException(
                "PAYFAST_VALIDATE_CALL_FAILED: network error m_payment_id=${params["m_payment_id"]}: ${e.message}", e)
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
