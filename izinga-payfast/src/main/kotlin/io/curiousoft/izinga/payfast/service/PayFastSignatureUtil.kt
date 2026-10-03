package io.curiousoft.izinga.payfast.service

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * TIER-BILLING-01 / REQ-02, REQ-04: Generates and validates PayFast MD5 signatures.
 *
 * Algorithm (PayFast spec):
 * 1. Collect all parameters except "signature".
 * 2. Sort alphabetically by key.
 * 3. URL-encode each value with UTF-8, then join as key=value pairs with "&".
 * 4. Append "&passphrase=<url-encoded-passphrase>" if passphrase is non-blank.
 * 5. Compute MD5 of the resulting string, return lowercase hex.
 *
 * SEC-TB01-03: passphrase is injected at construction time and never logged.
 */
class PayFastSignatureUtil(private val passphrase: String) {

    /**
     * Computes the PayFast MD5 signature over [params].
     *
     * @param params all form parameters including "signature" key (it will be excluded automatically).
     * @return lowercase hex MD5 signature string.
     */
    fun computeSignature(params: Map<String, String>): String {
        val paramString = buildParamString(params)
        return md5(paramString)
    }

    /**
     * Validates whether the [signature] in the incoming map matches a freshly computed signature
     * over the same map (excluding the "signature" entry itself).
     *
     * @param params full ITN parameter map, including the "signature" field.
     * @return true if the computed signature matches the provided "signature" value.
     */
    fun isValidSignature(params: Map<String, String>): Boolean {
        val provided = params["signature"] ?: return false
        val computed = computeSignature(params)
        return computed.equals(provided, ignoreCase = true)
    }

    /**
     * Builds the parameter string used for signature computation:
     * sorted keys, URL-encoded values, passphrase appended if non-blank.
     * Excludes the "signature" key.
     */
    fun buildParamString(params: Map<String, String>): String {
        val sb = StringBuilder()
        params.entries
            .filter { it.key != "signature" }
            .sortedBy { it.key }
            .forEach { (key, value) ->
                if (sb.isNotEmpty()) sb.append("&")
                sb.append(key).append("=").append(urlEncode(value))
            }
        if (passphrase.isNotBlank()) {
            sb.append("&passphrase=").append(urlEncode(passphrase))
        }
        return sb.toString()
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
