package io.curiousoft.izinga.yocopay.config

import feign.RequestInterceptor
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.core5.util.Timeout
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*


@ConfigurationProperties(prefix = "yoco.api")
data class YocoConfiguration(val url: String, val key: String, val webhooksec: String)

@ConfigurationProperties(prefix = "yoco.dashboard-api")
data class YocoDashBoardConfiguration(val url: String, val user: String, val token: String, val businessuuid: String)

private val logger = LoggerFactory.getLogger(YocoConfiguration::class.java)

fun YocoConfiguration.checksum(data: String)= MessageDigest.getInstance("MD5")
    .digest("$data$key".toByteArray())
    .let {  String(Base64.getEncoder().encode(it))}

fun YocoConfiguration.isValidOrigin(webhookId: String, webhookTimestamp: String, body: String, signature: String): Boolean {
    return yocoHash(webhookId = webhookId, webhookTimestamp = webhookTimestamp, body = body)
        .let {
            signature.split(" ").map { it.split(",")[1] }.any { it.lowercase() == it }
        }
}

fun YocoConfiguration.yocoHash(webhookId: String, webhookTimestamp: String, body: String): String {
    val data = "$webhookId.$webhookTimestamp.$body"
    val digest  = SHA256Digest()

    val hMac = HMac(digest).also {
        it.init(KeyParameter(Base64.getDecoder().decode(webhooksec)))
        val hmacIn: ByteArray = data.toByteArray()
        it.update(hmacIn, 0, hmacIn.size)
    }

    return ByteArray(hMac.macSize).let {
        hMac.doFinal(it, 0)
        String(Base64.getEncoder().encode(it))
    }
}

private fun convertToHex(messageDigest: ByteArray): String {
    val bigint = BigInteger(1, messageDigest)
    var hexText = bigint.toString(16)
    while (hexText.length < 32) {
        hexText = "0$hexText"
    }
    return hexText
}

@Configuration
class YocoHeaderConfig {

    /**
     * ADR-031 / REQ-01 (Option C): connect timeout for OpenAI API calls, in seconds.
     * Tunable via {@code izinga.ai.openai.connect-timeout-seconds} without a release.
     * Default 3 s — ample for TCP connect; a longer value only delays fail-fast on
     * DNS/routing failures.
     */
    @Value("\${izinga.ai.openai.connect-timeout-seconds:3}")
    var openAiConnectTimeoutSeconds: Long = 3

    /**
     * ADR-031 / REQ-01 (Option C): response timeout for OpenAI API calls, in seconds.
     * Tunable via {@code izinga.ai.openai.response-timeout-seconds} without a release.
     * Default 15 s — raised from 8 s after QA measured real MCP tool-use round-trips of
     * 6.73 s (only 1.27 s margin under the old 8 s limit).
     * Worst-case with 3 attempts + 2 s + 4 s backoff: 15 × 3 + 2 + 4 = 51 s.
     * This flow is async (Meta webhook ack is already returned) so 51 s is acceptable.
     */
    @Value("\${izinga.ai.openai.response-timeout-seconds:15}")
    var openAiResponseTimeoutSeconds: Long = 15

    @Bean
    fun basicAuthRequestInterceptor(@Value("\${yoco.api.key}") apiKey: String, yocoDashBoard: YocoDashBoardConfiguration): RequestInterceptor = RequestInterceptor {
        it.header("Authorization", "Bearer $apiKey")
        it.header("x-auth-token", yocoDashBoard.token)
        it.header("useruuid", yocoDashBoard.user)
        it.header("businessuuid", yocoDashBoard.businessuuid)
    };

    // ADR-031: @Primary ensures all currently-unqualified RestTemplate injection points
    // continue to resolve here with ZERO change to their injection sites.
    @Primary
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate().apply {
        val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(200)
            .setMaxConnPerRoute(20)
            .build()
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build()
        requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
    }

    /**
     * ADR-031 / REQ-01-03: Dedicated RestTemplate for OpenAI calls only.
     * Explicit timeouts: connect {@link #openAiConnectTimeoutSeconds} s (default 3 s,
     * fail-fast on DNS/routing), response {@link #openAiResponseTimeoutSeconds} s (default 15 s).
     * Both are tunable via {@code izinga.ai.openai.*} properties in {@code application.yml}
     * without a release.
     * Worst-case: 15 s × 3 attempts + 2 s + 4 s backoff = 51 s.
     * Acceptable — this flow is async and does NOT block Meta's webhook ack.
     * Injected via @Qualifier("openAiRestTemplate") in AiCustomerServiceAgent and StoreAiAgent.
     */
    @Bean("openAiRestTemplate")
    fun openAiRestTemplate(): RestTemplate {
        // ADR-031 (Option C): log the resolved timeout values at startup so QA and ops can confirm
        // the properties are bound correctly. If the @Value is unbound, Kotlin defaults apply (3/15).
        logger.info("ADR-031: openAiRestTemplate timeouts — connect={}s response={}s " +
                "(izinga.ai.openai.connect-timeout-seconds / response-timeout-seconds)",
            openAiConnectTimeoutSeconds, openAiResponseTimeoutSeconds)
        return RestTemplate().apply {
            val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(50)
                .setMaxConnPerRoute(10)
                .build()
            val requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(openAiConnectTimeoutSeconds))
                .setResponseTimeout(Timeout.ofSeconds(openAiResponseTimeoutSeconds))
                .build()
            val httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build()
            requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
        }
    }
}