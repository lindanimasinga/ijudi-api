package io.curiousoft.izinga.messaging.aiAgent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the POPIA-safe masking and JWT-redaction utilities in
 * {@link AiCustomerServiceAgent}.
 *
 * Coverage: maskPhoneInText and redactJwtInUrl static helpers.
 * C-1 (Legal): phone numbers must be masked to last 4 digits in all log output.
 * C-2 (Legal): JWT tokens embedded in scope= URL parameters must be redacted.
 */
class AiCustomerServiceAgentLoggingTest {

    // ─── maskPhoneInText ────────────────────────────────────────────────────────

    @Test
    void maskPhoneInText_nullInput_returnsNull() {
        assertNull(AiCustomerServiceAgent.maskPhoneInText(null));
    }

    @Test
    void maskPhoneInText_emptyString_returnsEmpty() {
        assertEquals("", AiCustomerServiceAgent.maskPhoneInText(""));
    }

    @Test
    void maskPhoneInText_noPhoneNumbers_returnsUnchanged() {
        String input = "Hello, how can I help you?";
        assertEquals(input, AiCustomerServiceAgent.maskPhoneInText(input));
    }

    @Test
    void maskPhoneInText_localSouthAfricanNumber_masksAllButLastFour() {
        // 11-digit number: 27812815707 → ***5707
        String result = AiCustomerServiceAgent.maskPhoneInText("27812815707");
        assertEquals("***5707", result);
    }

    @Test
    void maskPhoneInText_internationalPrefixNumber_masksAllButLastFour() {
        // +27 prefix variant: +27812815707 → ***5707
        String result = AiCustomerServiceAgent.maskPhoneInText("+27812815707");
        assertEquals("***5707", result);
    }

    @Test
    void maskPhoneInText_phoneEmbeddedInText_onlyMasksPhonePart() {
        String result = AiCustomerServiceAgent.maskPhoneInText("User phone is +27812815707 and name is Lindani");
        assertEquals("User phone is ***5707 and name is Lindani", result);
    }

    @Test
    void maskPhoneInText_multiplePhoneNumbers_masksEachOne() {
        String result = AiCustomerServiceAgent.maskPhoneInText("from=+27812815707 adminPhone=0812815707");
        // Both phones should be masked
        assertFalse(result.contains("27812815707"), "first phone must be masked");
        assertFalse(result.contains("0812815707"), "second phone must be masked");
        assertTrue(result.contains("***5707"), "masked suffix must be present");
    }

    @Test
    void maskPhoneInText_shortStringUnder7Digits_notMasked() {
        // 6-digit number is below the 7-digit minimum threshold — not a phone
        String input = "code=123456";
        assertEquals(input, AiCustomerServiceAgent.maskPhoneInText(input));
    }

    @Test
    void maskPhoneInText_sevenDigitNumber_isMasked() {
        // 7-digit is at the minimum threshold — should be masked
        String result = AiCustomerServiceAgent.maskPhoneInText("1234567");
        assertEquals("***4567", result);
    }

    // ─── redactJwtInUrl ─────────────────────────────────────────────────────────

    @Test
    void redactJwtInUrl_nullInput_returnsNull() {
        assertNull(AiCustomerServiceAgent.redactJwtInUrl(null));
    }

    @Test
    void redactJwtInUrl_noScopeParam_returnsUnchanged() {
        String url = "https://api.izinga.co.za/mcp/tools";
        assertEquals(url, AiCustomerServiceAgent.redactJwtInUrl(url));
    }

    @Test
    void redactJwtInUrl_scopeAsFirstParam_redactsToken() {
        String url = "https://api.izinga.co.za/mcp?scope=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.abc123";
        String result = AiCustomerServiceAgent.redactJwtInUrl(url);
        assertEquals("https://api.izinga.co.za/mcp?scope=[REDACTED]", result);
    }

    @Test
    void redactJwtInUrl_scopeAsSubsequentParam_redactsToken() {
        String url = "https://api.izinga.co.za/mcp?foo=bar&scope=eyJhbGciOiJIUzI1NiJ9.secret&baz=qux";
        String result = AiCustomerServiceAgent.redactJwtInUrl(url);
        // scope value must be redacted; foo and baz must be preserved
        assertTrue(result.contains("?foo=bar"), "other params must be preserved");
        assertTrue(result.contains("scope=[REDACTED]"), "scope token must be redacted");
        assertTrue(result.contains("&baz=qux"), "trailing params must be preserved");
        assertFalse(result.contains("eyJhbGciOiJIUzI1NiJ9"), "JWT must not appear in output");
    }

    @Test
    void redactJwtInUrl_jwtValueNeverAppearInOutput() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIrMjc4MTI4MTU3MDciLCJleHAiOjE3MDAwMDAwMDB9.signature";
        String url = "https://api.izinga.co.za/mcp?scope=" + jwt;
        String result = AiCustomerServiceAgent.redactJwtInUrl(url);
        assertFalse(result.contains(jwt), "complete JWT must not appear in redacted output");
        assertFalse(result.contains("eyJ"), "JWT prefix must not appear in redacted output");
        assertTrue(result.contains("scope=[REDACTED]"), "redacted marker must be present");
    }

    @Test
    void redactJwtInUrl_urlStructurePreservedAfterRedaction() {
        // Host and path must survive redaction unchanged
        String url = "https://api.izinga.co.za/mcp?scope=sometoken";
        String result = AiCustomerServiceAgent.redactJwtInUrl(url);
        assertTrue(result.startsWith("https://api.izinga.co.za/mcp"), "URL host and path must be preserved");
    }
}
