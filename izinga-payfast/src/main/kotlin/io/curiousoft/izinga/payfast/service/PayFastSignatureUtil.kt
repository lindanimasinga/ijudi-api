package io.curiousoft.izinga.payfast.service

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * TIER-BILLING-01 / REQ-02, REQ-04: Generates and validates PayFast MD5 signatures.
 *
 * IMPORTANT — PayFast operates two distinct signature schemes that MUST NOT be mixed:
 *
 * Scheme 1 — Custom Payment Integration (hosted-checkout redirect, what PayFastCheckoutService uses):
 *   Fields are processed in PAYFAST'S DOCUMENTED FIELD ORDER:
 *     Merchant Details → Buyer Details → Transaction Details → Transaction Options → Recurring Billing
 *   This is explicitly NOT alphabetical order.
 *   The caller (PayFastCheckoutService.buildPayFastParams) is responsible for passing a LinkedHashMap
 *   built in documented order; buildParamString() then preserves that insertion order.
 *   PayFast's own documentation warns: "Do not use the custom payment signature format when
 *   implementing the API, and vice versa."
 *   Used by: buildParamString() / computeSignature()
 *
 * Scheme 2 — Instant Transaction Notifications (ITN, inbound PayFast-to-us webhooks):
 *   PayFast computes the ITN signature by sorting all fields ALPHABETICALLY before hashing.
 *   When validating an incoming ITN we must recompute using the same alphabetical ordering so our
 *   hash matches what PayFast sent in the "signature" field.
 *   This also handles the fact that Java's HttpServletRequest.getParameterMap() does not guarantee
 *   iteration order, making insertion-order preservation impossible for incoming ITN params.
 *   Used by: buildParamStringSorted() / computeSignatureSorted() / isValidSignature()
 *
 * SEC-TB01-03: passphrase is injected at construction time and never logged.
 */
class PayFastSignatureUtil(private val passphrase: String) {

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Custom Payment Integration (checkout initiation — Scheme 1)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Computes the PayFast MD5 signature over [params] preserving the caller's map iteration order
     * (insertion order for LinkedHashMap).
     *
     * FOR CHECKOUT INITIATION ONLY. The caller must pass a LinkedHashMap built in PayFast's
     * documented field order. Do NOT use this method to validate incoming ITN webhooks — use
     * [isValidSignature] instead.
     *
     * @param params all form parameters excluding "signature" (it will be excluded automatically).
     * @return lowercase hex MD5 signature string.
     */
    fun computeSignature(params: Map<String, String>): String {
        val paramString = buildParamString(params)
        return md5(paramString)
    }

    /**
     * Builds the parameter string for Custom Payment Integration signature computation.
     *
     * Preserves the iteration order of [params] (insertion order for LinkedHashMap) — this matches
     * PayFast's documented field order requirement for hosted-checkout redirect. Excludes the
     * "signature" key. Appends "&passphrase=<url-encoded-passphrase>" if passphrase is non-blank.
     *
     * URL-encoding: spaces encode as '+' (matching PHP urlencode() / java.net.URLEncoder default).
     * Do NOT replace '+' with '%20' — that diverges from PayFast's algorithm and causes mismatches.
     */
    fun buildParamString(params: Map<String, String>): String {
        val sb = StringBuilder()
        params.entries
            .filter { it.key != "signature" }
            .forEach { (key, value) ->
                if (sb.isNotEmpty()) sb.append("&")
                sb.append(key).append("=").append(urlEncode(value))
            }
        if (passphrase.isNotBlank()) {
            sb.append("&passphrase=").append(urlEncode(passphrase))
        }
        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // ITN validation (inbound webhook — Scheme 2)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Validates whether the "signature" value in [params] matches a freshly computed signature
     * over the same map (excluding the "signature" entry itself), using ALPHABETICAL key ordering.
     *
     * FOR ITN VALIDATION ONLY. PayFast computes ITN signatures by sorting fields alphabetically;
     * this method replicates that ordering so the two hashes can be compared.
     *
     * @param params full ITN parameter map, including the "signature" field.
     * @return true if the computed signature matches the provided "signature" value.
     */
    fun isValidSignature(params: Map<String, String>): Boolean {
        val provided = params["signature"] ?: return false
        val computed = computeSignatureSorted(params)
        return computed.equals(provided, ignoreCase = true)
    }

    /**
     * Computes the PayFast MD5 signature over [params] sorted ALPHABETICALLY by key.
     *
     * FOR ITN VALIDATION AND ITN TEST SETUP ONLY. Use this when simulating what PayFast would
     * compute for an ITN signature (e.g. in test helpers that build signed ITN param maps).
     * Do NOT use this for checkout initiation — use [computeSignature] instead.
     *
     * @param params all form parameters excluding "signature" (it will be excluded automatically).
     * @return lowercase hex MD5 signature string.
     */
    fun computeSignatureSorted(params: Map<String, String>): String {
        val paramString = buildParamStringSorted(params)
        return md5(paramString)
    }

    /**
     * Builds the parameter string for ITN signature computation.
     *
     * Sorts keys ALPHABETICALLY — matching how PayFast computes its ITN "signature" field.
     * Excludes the "signature" key. Appends "&passphrase=<url-encoded-passphrase>" if non-blank.
     */
    fun buildParamStringSorted(params: Map<String, String>): String {
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

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Shared internals
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
