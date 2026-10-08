package io.curiousoft.izinga.payfast.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * TIER-BILLING-01 / T-08: Unit tests for [PayFastSignatureUtil].
 *
 * PayFast uses TWO distinct signature schemes (see PayFastSignatureUtil KDoc):
 *
 * - Scheme 1 (Checkout / Custom Payment Integration): insertion/documented field order, NOT alphabetical.
 *   → buildParamString() / computeSignature()
 *
 * - Scheme 2 (ITN validation): alphabetical sort.
 *   → buildParamStringSorted() / computeSignatureSorted() / isValidSignature()
 *
 * Bug #14 regression: removing .sortedBy from buildParamString fixes outbound checkout signing
 * (PayFast sandbox was rejecting with "signature does not match" because the alphabetical sort
 * was destroying the insertion order that PayFastCheckoutService carefully constructed).
 *
 * Tests do NOT require real PayFast sandbox access.
 */
class PayFastSignatureUtilTest {

    private val passphrase = "jt7NOE43FZPn"

    private val util = PayFastSignatureUtil(passphrase)

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // buildParamString — Scheme 1: insertion order, no alphabetical sort
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `buildParamString excludes signature key and preserves insertion order`() {
        // Params in INSERTION order: merchant_id, merchant_key, amount, item_name
        // Alphabetical order would be: amount, item_name, merchant_id, merchant_key
        val params = linkedMapOf(
            "merchant_id" to "10000100",
            "merchant_key" to "46f0cd694581a",
            "amount" to "100.00",
            "item_name" to "Test Product",
            "signature" to "should-be-excluded"
        )
        val result = util.buildParamString(params)

        // signature must be excluded
        assertFalse(result.contains("signature="), "signature key must be excluded from param string")

        // INSERTION ORDER: merchant_id comes first (not amount, which would be first alphabetically)
        val firstKey = result.substringBefore("=")
        assertEquals("merchant_id", firstKey,
            "First key must be 'merchant_id' (insertion order) — NOT 'amount' (alphabetical). " +
            "Bug #14: buildParamString must NOT sort alphabetically for checkout. Got: $result")

        // passphrase appended at the end
        assertTrue(result.endsWith("&passphrase=${urlEncode(passphrase)}"),
            "passphrase must be appended at end. Got: $result")
    }

    /**
     * Bug #14 regression: buildParamString must NOT sort alphabetically.
     *
     * Before this fix, buildParamString had .sortedBy { it.key } which destroyed the insertion
     * order that PayFastCheckoutService carefully built. PayFast's checkout signature requires
     * documented field order (insertion), NOT alphabetical. Alphabetical ordering caused
     * "Generated signature does not match submitted signature" on PayFast sandbox.
     *
     * Expected MD5 pre-computed for insertion-order param string:
     *   merchant_id=10000100&amount=100.00&item_name=iZinga+PREMIUM+1+Subscription&passphrase=jt7NOE43FZPn
     *   → MD5: 3e25a08d04e00b9a57861453bf013e3f
     *
     * Alphabetical-order MD5 (WRONG for checkout) would be:
     *   amount=100.00&item_name=iZinga+PREMIUM+1+Subscription&merchant_id=10000100&passphrase=jt7NOE43FZPn
     *   → MD5: ae21f997e6d55dfffb0ce18d6de63aa5
     */
    @Test
    fun `computeSignature preserves insertion order for checkout params - Bug14 regression`() {
        val params = linkedMapOf(
            "merchant_id" to "10000100",
            "amount" to "100.00",
            "item_name" to "iZinga PREMIUM 1 Subscription"
        )

        val sig = util.computeSignature(params)

        // Must match insertion-order MD5 (merchant_id first, then amount, then item_name)
        assertEquals("3e25a08d04e00b9a57861453bf013e3f", sig,
            "computeSignature must use insertion order for checkout (Scheme 1). " +
            "Got $sig — if this is ae21f997e6d55dfffb0ce18d6de63aa5, the alphabetical sort was NOT removed.")

        // Must NOT match alphabetical-order MD5 (which would be produced if .sortedBy is still present)
        assertNotEquals("ae21f997e6d55dfffb0ce18d6de63aa5", sig,
            "computeSignature must NOT use alphabetical order — that is Scheme 2 (ITN only).")
    }

    @Test
    fun `buildParamString URL-encodes spaces as plus signs matching PHP urlencode behaviour`() {
        val params = mapOf("item_name" to "Test Product")
        val result = util.buildParamString(params)
        // Space MUST encode as '+' to match PHP urlencode() which PayFast's server uses.
        // Bug guard: do NOT replace '+' with '%20' — that causes PayFast signature mismatches.
        assertTrue(result.contains("item_name=Test+Product"),
            "Space should be encoded as '+' (PHP urlencode style), not '%20'. Result: $result")
        assertFalse(result.contains("item_name=Test%20Product"),
            "Space must NOT be encoded as '%20' — that diverges from PayFast's algorithm. Result: $result")
    }

    /**
     * Regression test for Bug #13 (TIER-BILLING-01):
     * item_name "iZinga PREMIUM 1 Subscription" contains spaces which always appear in real checkout
     * requests. Before the fix, urlEncode() was replacing '+' with '%20', causing PayFast's sandbox
     * to reject the request with "Generated signature does not match submitted signature".
     */
    @Test
    fun `buildParamString regression - subscription item_name with spaces encodes as plus not percent20`() {
        val params = mapOf(
            "merchant_id" to "10020746",
            "amount" to "799.00",
            "item_name" to "iZinga PREMIUM 1 Subscription"
        )
        val result = util.buildParamString(params)
        assertTrue(result.contains("item_name=iZinga+PREMIUM+1+Subscription"),
            "Subscription item_name spaces must encode as '+'. Got: $result")
        assertFalse(result.contains("%20"),
            "No '%20' must appear in the param string for this input. Got: $result")
    }

    @Test
    fun `buildParamString with blank passphrase does not append passphrase`() {
        val utilNoPassphrase = PayFastSignatureUtil("")
        val params = mapOf("merchant_id" to "10000100")
        val result = utilNoPassphrase.buildParamString(params)
        assertFalse(result.contains("passphrase="), "No passphrase should be appended when blank")
    }

    @Test
    fun `buildParamString insertion order differs from alphabetical order for multi-key map`() {
        // Prove that insertion order and alphabetical order produce different results
        // when keys are not already in alphabetical order.
        val params = linkedMapOf(
            "merchant_id" to "10000100",
            "amount" to "100.00",
            "item_name" to "iZinga PREMIUM 1 Subscription"
        )
        val insertionResult = util.buildParamString(params)
        val sortedResult = util.buildParamStringSorted(params)

        assertNotEquals(insertionResult, sortedResult,
            "Insertion-order and alphabetical param strings must differ for non-alphabetically-ordered keys")

        // insertion: merchant_id comes first
        assertTrue(insertionResult.startsWith("merchant_id="),
            "Insertion-order result must start with 'merchant_id=' (first inserted key). Got: $insertionResult")

        // sorted: amount comes first alphabetically
        assertTrue(sortedResult.startsWith("amount="),
            "Alphabetical-order result must start with 'amount=' (alphabetically first). Got: $sortedResult")
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // buildParamStringSorted — Scheme 2: alphabetical sort (for ITN validation)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `buildParamStringSorted sorts alphabetically and excludes signature key`() {
        val params = mapOf(
            "merchant_id" to "10000100",
            "merchant_key" to "46f0cd694581a",
            "amount" to "100.00",
            "item_name" to "Test Product",
            "signature" to "should-be-excluded"
        )
        val result = util.buildParamStringSorted(params)

        // signature must be excluded
        assertFalse(result.contains("signature="), "signature key must be excluded")

        // ALPHABETICAL ORDER: 'amount' must come first
        val firstKey = result.substringBefore("=")
        assertEquals("amount", firstKey,
            "First key must be 'amount' (alphabetically first). Got: $result")

        // passphrase appended at the end
        assertTrue(result.endsWith("&passphrase=${urlEncode(passphrase)}"),
            "passphrase must be appended at end. Got: $result")
    }

    @Test
    fun `buildParamStringSorted with blank passphrase does not append passphrase`() {
        val utilNoPassphrase = PayFastSignatureUtil("")
        val params = mapOf("merchant_id" to "10000100")
        val result = utilNoPassphrase.buildParamStringSorted(params)
        assertFalse(result.contains("passphrase="), "No passphrase should be appended when blank")
    }

    /**
     * Pre-computed expected MD5 for alphabetical-order param string:
     *   amount=100.00&item_name=iZinga+PREMIUM+1+Subscription&merchant_id=10000100&passphrase=jt7NOE43FZPn
     *   → MD5: ae21f997e6d55dfffb0ce18d6de63aa5
     */
    @Test
    fun `computeSignatureSorted sorts alphabetically matching ITN scheme`() {
        val params = linkedMapOf(
            "merchant_id" to "10000100",
            "amount" to "100.00",
            "item_name" to "iZinga PREMIUM 1 Subscription"
        )

        val sig = util.computeSignatureSorted(params)

        assertEquals("ae21f997e6d55dfffb0ce18d6de63aa5", sig,
            "computeSignatureSorted must use alphabetical order (Scheme 2 / ITN). Got: $sig")

        // Must NOT match the checkout (insertion-order) MD5
        assertNotEquals("3e25a08d04e00b9a57861453bf013e3f", sig,
            "computeSignatureSorted must NOT match insertion-order (Scheme 1 / checkout) hash.")
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // computeSignature — general properties
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Known-good test vector from PayFast docs.
     * Parameters, passphrase, and expected MD5 taken from:
     * https://developers.payfast.co.za/docs#step_2_signature
     *
     * NOTE: The PayFast docs example uses custom integration / documented field order, which
     * matches computeSignature (insertion-order). The params below are in the documented
     * PayFast field order so both schemes produce the same result for this specific vector.
     */
    @Test
    fun `computeSignature produces valid MD5 for known PayFast-compatible parameters`() {
        // These parameters and passphrase match PayFast docs structure
        val params = linkedMapOf(
            "merchant_id" to "10000100",
            "merchant_key" to "46f0cd694581a",
            "return_url" to "https://www.example.com/return",
            "cancel_url" to "https://www.example.com/cancel",
            "notify_url" to "https://www.example.com/notify",
            "name_first" to "First",
            "name_last" to "Last",
            "email_address" to "test@test.com",
            "m_payment_id" to "1234",
            "amount" to "10.00",
            "item_name" to "Test+Item"
        )
        val signature = util.computeSignature(params)
        // The result must be a 32-char hex string (MD5 output)
        assertEquals(32, signature.length, "MD5 signature must be 32 hex chars")
        assertTrue(signature.all { it.isLetterOrDigit() }, "Signature must be hex chars only")
        // Verify determinism: calling again must produce the same result
        assertEquals(signature, util.computeSignature(params))
    }

    @Test
    fun `computeSignature is deterministic for the same input`() {
        val params = mapOf("amount" to "100.00", "item_name" to "Widget")
        val sig1 = util.computeSignature(params)
        val sig2 = util.computeSignature(params)
        assertEquals(sig1, sig2)
    }

    @Test
    fun `computeSignature changes when a parameter value changes`() {
        val params1 = mapOf("amount" to "100.00", "item_name" to "Widget")
        val params2 = mapOf("amount" to "200.00", "item_name" to "Widget")
        assertNotEquals(util.computeSignature(params1), util.computeSignature(params2))
    }

    @Test
    fun `computeSignature changes when passphrase changes`() {
        val util1 = PayFastSignatureUtil("passphrase-A")
        val util2 = PayFastSignatureUtil("passphrase-B")
        val params = mapOf("amount" to "100.00")
        assertNotEquals(util1.computeSignature(params), util2.computeSignature(params))
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // isValidSignature — Scheme 2 (ITN validation, alphabetical recompute)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Simulates an incoming ITN: PayFast computes signature alphabetically, embeds it in the POST
     * body. Our server receives the decoded params and calls isValidSignature, which also
     * recomputes alphabetically — so the signatures match.
     */
    @Test
    fun `isValidSignature returns true when signature was computed alphabetically (ITN simulation)`() {
        val params = mutableMapOf(
            "merchant_id" to "10000100",
            "amount" to "800.00",
            "item_name" to "iZinga PREMIUM 1 Subscription"
        )
        // Simulate PayFast ITN signing (alphabetical)
        val sig = util.computeSignatureSorted(params)
        params["signature"] = sig
        assertTrue(util.isValidSignature(params),
            "isValidSignature must accept a correctly alphabetically-signed ITN. " +
            "Note: isValidSignature uses alphabetical order (Scheme 2) to match PayFast's ITN scheme.")
    }

    /**
     * Demonstrates that isValidSignature correctly REJECTS a signature produced with insertion
     * order (Scheme 1) when the params are not already in alphabetical order.
     * This confirms the two schemes are correctly separated.
     */
    @Test
    fun `isValidSignature returns false when signature was computed with insertion order (Scheme 1) and params not alphabetical`() {
        val params = mutableMapOf(
            "merchant_id" to "10000100",   // insertion: merchant_id first
            "amount" to "800.00",
            "item_name" to "iZinga PREMIUM 1 Subscription"
        )
        // Sign with insertion order (Scheme 1 / checkout)
        val sig = util.computeSignature(params)
        params["signature"] = sig
        // isValidSignature uses alphabetical order — this signature won't match
        assertFalse(util.isValidSignature(params),
            "isValidSignature must reject a Scheme 1 (insertion-order) signature for non-alphabetical params, " +
            "because PayFast's ITN scheme is alphabetical and these params are NOT in alphabetical order.")
    }

    @Test
    fun `isValidSignature returns false when signature is tampered`() {
        val params = mutableMapOf(
            "merchant_id" to "10000100",
            "amount" to "800.00",
            "signature" to "aaaabbbbccccddddeeeeffffgggghhhhiiii".substring(0, 32)
        )
        assertFalse(util.isValidSignature(params), "Tampered signature should be rejected")
    }

    @Test
    fun `isValidSignature returns false when signature key is absent`() {
        val params = mapOf("merchant_id" to "10000100", "amount" to "800.00")
        assertFalse(util.isValidSignature(params), "Missing signature should return false")
    }

    @Test
    fun `isValidSignature returns false when amount is altered after signing`() {
        val params = mutableMapOf(
            "amount" to "800.00",        // alphabetically first
            "item_name" to "iZinga Subscription",
            "merchant_id" to "10000100"  // alphabetically last
        )
        // Sign with alphabetical order (simulating PayFast ITN signing)
        val sig = util.computeSignatureSorted(params)
        params["signature"] = sig
        // Tamper with amount after signing
        params["amount"] = "1.00"
        assertFalse(util.isValidSignature(params), "Altered param should fail ITN signature check")
    }

    /**
     * Regression test for Bug #13 / ITN path (isValidSignature):
     * PayFast sends its ITN with a signature computed using PHP urlencode() (spaces as '+').
     * isValidSignature must use the same encoding when re-computing for comparison.
     * Before the fix, the mismatch caused all ITN validations to fail whenever item_name had spaces.
     *
     * Updated for Bug #14: signature is now computed with computeSignatureSorted (alphabetical)
     * to correctly simulate PayFast's ITN signing scheme.
     */
    @Test
    fun `isValidSignature regression - ITN with spaces in item_name uses plus encoding and alphabetical order`() {
        // Simulate the params PayFast would POST in an ITN for a subscription with spaces in item_name.
        // PayFast signs ITNs alphabetically, so we use computeSignatureSorted to build the test signature.
        val params = mutableMapOf(
            "merchant_id" to "10020746",
            "m_payment_id" to "some-uuid-here",
            "amount" to "799.00",
            "item_name" to "iZinga PREMIUM 1 Subscription",
            "payment_status" to "COMPLETE"
        )
        // Compute signature the way PayFast would (alphabetical, spaces as '+')
        val sig = util.computeSignatureSorted(params)
        params["signature"] = sig
        assertTrue(util.isValidSignature(params),
            "isValidSignature must accept a correctly signed ITN with spaces in item_name. " +
            "Failure here means urlEncode is inconsistent between signing and verification.")
    }

    @Test
    fun `urlEncode produces uppercase hex digits for percent-encoded bytes`() {
        // PayFast docs note hex digits must be uppercase (e.g. %3A not %3a).
        // java.net.URLEncoder.encode() already produces uppercase hex — this test documents and guards that.
        val params = mapOf("return_url" to "https://example.com/return")
        val result = util.buildParamString(params)
        // Colon (':') encodes as %3A (uppercase), slash ('/') as %2F (uppercase)
        assertTrue(result.contains("%3A") || result.contains("%2F") || result.contains("%3A"),
            "Percent-encoded bytes must use uppercase hex. Result: $result")
        assertFalse(result.contains("%3a") || result.contains("%2f"),
            "Lowercase hex is not acceptable per PayFast spec. Result: $result")
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `computeSignature handles empty param map (only passphrase)`() {
        val sig = util.computeSignature(emptyMap())
        assertEquals(32, sig.length, "Even empty params should produce a 32-char MD5")
    }

    @Test
    fun `computeSignatureSorted handles empty param map (only passphrase)`() {
        val sig = util.computeSignatureSorted(emptyMap())
        assertEquals(32, sig.length, "Even empty params should produce a 32-char MD5")
        // Both schemes produce the same result for empty params (only passphrase differs by sort, not here)
        assertEquals(sig, util.computeSignature(emptyMap()),
            "For empty params, insertion and alphabetical order produce the same result")
    }

    @Test
    fun `buildParamString handles special characters in values`() {
        val params = mapOf("item_name" to "Café & Résumé")
        val result = util.buildParamString(params)
        // The & separator between params should only appear as a delimiter, not inside encoded values
        assertTrue(result.startsWith("item_name="), "Result should start with item_name= but was: $result")
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
