package io.curiousoft.izinga.yocopay.config

import org.apache.hc.client5.http.config.Configurable
import org.apache.hc.core5.util.Timeout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory

class YocoConfigurationTest {

    @Test
    internal fun testYocoSignature() {
        //given
        val data = """{"createdDate":"2023-11-03T14:09:29.391675Z","id":"evt_rLQQMyMj2j1iynQUQJGCPmAL","payload":{"amount":7764,"createdDate":"2023-11-03T14:09:06.040866Z","currency":"ZAR","id":"p_3LRQ3y3jjn4cN1PinevSqMAx","metadata":{"checkoutId":"ch_Kx6mdodJNWQCkAFbpvh3wOg8","orderId":"1699017774","productType":"checkout"},"mode":"test","paymentMethodDetails":{"card":{"expiryMonth":11,"expiryYear":24,"maskedCard":"************1111","scheme":"visa"},"type":"card"},"status":"succeeded","type":"payment"},"type":"payment.succeeded"}"""

        //when
        val yocoConf = YocoConfiguration(key = "key", url = "url", webhooksec = "M0JGQUI3MTcwODQxMDg2MUMyNjk2OUQ4MzA1NTI0QUE")
        val hash = yocoConf.yocoHash(webhookId = "msg_2XfWMtEcCBTQrgL9iM00gDMAQso",
            webhookTimestamp = "1699020569", body = data)

        //verify
        Assertions.assertEquals("8AvKYTPMYVtjgiXM7KmaPMQLM+pVfmUOEVl6SalovSs=", hash)
    }

    /**
     * ADR-031 / REQ-01 (Option C): default timeout values must be 3 s connect and 15 s response.
     * Guards against a silently-unbound @Value property that would leave the timeout at the
     * Apache HC5 default of -1 (unlimited) — restoring the original bug in disguise.
     *
     * Uses Apache HC5's public [Configurable] interface on InternalHttpClient to avoid
     * brittle reflection. If this cast ever fails, the bean wiring has changed and needs
     * investigation.
     */
    @Test
    fun `openAiRestTemplate default connect timeout is 3s and response timeout is 15s`() {
        // Given: YocoHeaderConfig with field defaults unchanged (3L and 15L)
        val config = YocoHeaderConfig()
        // config.openAiConnectTimeoutSeconds == 3L (Kotlin default matches @Value default)
        // config.openAiResponseTimeoutSeconds == 15L (Kotlin default matches @Value default)

        // When
        val template = config.openAiRestTemplate()

        // Then
        val factory = template.requestFactory as HttpComponentsClientHttpRequestFactory
        val configurable = factory.httpClient as Configurable
        val reqConfig = configurable.config

        assertEquals(Timeout.ofSeconds(3L), reqConfig.connectTimeout,
            "Connect timeout must be 3 s (default)")
        assertEquals(Timeout.ofSeconds(15L), reqConfig.responseTimeout,
            "Response timeout must be 15 s (default) — not the unlimited Apache HC5 default")
        // Explicit unlimited guard: an unbound @Value property would leave timeout as Timeout.DISABLED
        assertNotEquals(Timeout.DISABLED, reqConfig.responseTimeout,
            "Response timeout must NOT be Timeout.DISABLED (unlimited) — a silently-unbound " +
            "@Value property would restore the original bug")
    }

    /**
     * ADR-031 / REQ-01 (Option C): when the @Value-injected fields are overridden (simulating
     * a property set in application.yml), those values must take effect in the bean — proving
     * the timeout is NOT hardcoded.
     *
     * Without configurable properties, the bean would always produce 15 s regardless of field
     * state. This test proves the bean reads the fields at call time.
     */
    @Test
    fun `openAiRestTemplate override timeout values are used when fields are changed`() {
        // Given: timeout fields overridden (simulates izinga.ai.openai.response-timeout-seconds=22)
        val config = YocoHeaderConfig()
        config.openAiConnectTimeoutSeconds = 7L
        config.openAiResponseTimeoutSeconds = 22L

        // When
        val template = config.openAiRestTemplate()

        // Then
        val factory = template.requestFactory as HttpComponentsClientHttpRequestFactory
        val configurable = factory.httpClient as Configurable
        val reqConfig = configurable.config

        assertEquals(Timeout.ofSeconds(7L), reqConfig.connectTimeout,
            "Connect timeout must reflect the overridden field value (7 s)")
        assertEquals(Timeout.ofSeconds(22L), reqConfig.responseTimeout,
            "Response timeout must reflect the overridden field value (22 s) — " +
            "if this is 15 s, the bean ignores the fields (hardcoded)")
    }
}
