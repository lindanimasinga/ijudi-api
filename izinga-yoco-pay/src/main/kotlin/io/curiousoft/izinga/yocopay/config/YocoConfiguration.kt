package io.curiousoft.izinga.yocopay.config

import feign.RequestInterceptor
import okhttp3.OkHttpClient
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*


@ConstructorBinding
@ConfigurationProperties(prefix = "yoco.api")
data class YocoConfiguration(val url: String, val key: String, val webhooksec: String)

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
class HeaderConfig {
    @Bean
    fun basicAuthRequestInterceptor(@Value("\${yoco.api.key}") apiKey: String): RequestInterceptor = RequestInterceptor {
        it.header("Authorization", "Bearer $apiKey")
    };
}