package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

/**
 * ADR-024: Tests for the new caller-identity enforcement methods in StoreScopeValidator.
 *
 * Uses JUnit 4 + Mockito to match the existing module style.
 */
@RunWith(MockitoJUnitRunner.class)
public class StoreScopeValidatorTest {

    @Before
    public void before() {
        StoreScopeContext.clear();
    }

    @After
    public void after() {
        StoreScopeContext.clear();
    }

    // ---- normalizeLast9 ----

    @Test
    public void normalizeLast9_returns9Digits() {
        assertEquals("812345678", StoreScopeValidator.normalizeLast9("+27812345678"));
    }

    @Test
    public void normalizeLast9_shortPhone_returnsNull() {
        assertNull(StoreScopeValidator.normalizeLast9("12345678")); // only 8 chars
    }

    @Test
    public void normalizeLast9_null_returnsNull() {
        assertNull(StoreScopeValidator.normalizeLast9(null));
    }

    @Test
    public void normalizeLast9_exactly9Chars_returnsSame() {
        assertEquals("812345678", StoreScopeValidator.normalizeLast9("812345678"));
    }

    // ---- validateCallerPhone: STORE audience (no-op) ----

    @Test
    public void validateCallerPhone_storeAudience_noOpEvenWithoutCallerPhone() {
        StoreScopeContext.setAudience(Audience.STORE);
        // Must not throw regardless of callerPhone state
        StoreScopeValidator.validateCallerPhone("+27812345678");
    }

    @Test
    public void validateCallerPhone_nullAudience_noOp() {
        // null audience = no restriction; no-op
        StoreScopeValidator.validateCallerPhone("+27812345678");
    }

    // ---- validateCallerPhone: CUSTOMER audience ----

    @Test
    public void validateCallerPhone_customerAudience_nullCallerPhone_hardReject() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        // callerPhone not set in context → hard reject
        try {
            StoreScopeValidator.validateCallerPhone("+27812345678");
            fail("Expected CallerIdentityViolationException");
        } catch (CallerIdentityViolationException e) {
            assertTrue(e.getMessage().startsWith("IDENTITY_REFUSAL:"));
        }
    }

    @Test
    public void validateCallerPhone_customerAudience_samePhone_passes() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone("+27812345678");
        // Same phone — must not throw
        StoreScopeValidator.validateCallerPhone("+27812345678");
    }

    @Test
    public void validateCallerPhone_customerAudience_differentPrefixSameDigits_passes() {
        // Both normalize to last-9 = 812345678
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone("+27812345678");
        StoreScopeValidator.validateCallerPhone("0812345678");
    }

    @Test
    public void validateCallerPhone_customerAudience_thirdPartyPhone_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone("+27812345678");
        try {
            StoreScopeValidator.validateCallerPhone("+27811111111"); // different number
            fail("Expected CallerIdentityViolationException");
        } catch (CallerIdentityViolationException e) {
            assertTrue(e.getMessage().startsWith("IDENTITY_REFUSAL:"));
        }
    }

    // ---- validateCallerPhone: DRIVER audience ----

    @Test
    public void validateCallerPhone_driverAudience_nullCallerPhone_hardReject() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        try {
            StoreScopeValidator.validateCallerPhone("+27812345678");
            fail("Expected CallerIdentityViolationException");
        } catch (CallerIdentityViolationException e) {
            assertTrue(e.getMessage().startsWith("IDENTITY_REFUSAL:"));
        }
    }

    @Test
    public void validateCallerPhone_driverAudience_samePhone_passes() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone("+27820000001");
        StoreScopeValidator.validateCallerPhone("+27820000001");
    }

    @Test
    public void validateCallerPhone_driverAudience_thirdPartyPhone_throws() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone("+27820000001");
        try {
            StoreScopeValidator.validateCallerPhone("+27899999999");
            fail("Expected CallerIdentityViolationException");
        } catch (CallerIdentityViolationException e) {
            assertTrue(e.getMessage().startsWith("IDENTITY_REFUSAL:"));
        }
    }

    // ---- validateCallerOwnsId ----

    @Test
    public void validateCallerOwnsId_storeAudience_noOp() {
        StoreScopeContext.setAudience(Audience.STORE);
        // Must not throw for STORE audience
        StoreScopeValidator.validateCallerOwnsId("some-other-id", () -> "caller-id");
    }

    @Test
    public void validateCallerOwnsId_nullAudience_noOp() {
        // No audience set → no enforcement
        StoreScopeValidator.validateCallerOwnsId("any-id", () -> "caller-id");
    }

    @Test
    public void validateCallerOwnsId_customerAudience_ownId_passes() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeValidator.validateCallerOwnsId("user-abc", () -> "user-abc");
    }

    @Test
    public void validateCallerOwnsId_customerAudience_differentId_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        try {
            StoreScopeValidator.validateCallerOwnsId("user-evil", () -> "user-abc");
            fail("Expected CallerIdentityViolationException");
        } catch (CallerIdentityViolationException e) {
            assertTrue(e.getMessage().startsWith("IDENTITY_REFUSAL:"));
        }
    }

    @Test
    public void validateCallerOwnsId_driverAudience_ownId_passes() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeValidator.validateCallerOwnsId("driver-xyz", () -> "driver-xyz");
    }

    @Test
    public void validateCallerOwnsId_driverAudience_differentId_throws() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        try {
            StoreScopeValidator.validateCallerOwnsId("driver-other", () -> "driver-xyz");
            fail("Expected CallerIdentityViolationException");
        } catch (CallerIdentityViolationException e) {
            assertTrue(e.getMessage().startsWith("IDENTITY_REFUSAL:"));
        }
    }

    @Test
    public void validateCallerOwnsId_resolverReturnsNull_throws() {
        // Caller could not be resolved → null does not equal the supplied id
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        try {
            StoreScopeValidator.validateCallerOwnsId("user-abc", () -> null);
            fail("Expected CallerIdentityViolationException");
        } catch (CallerIdentityViolationException e) {
            assertTrue(e.getMessage().startsWith("IDENTITY_REFUSAL:"));
        }
    }

    // ---- message text contract (ADR-024 non-negotiable) ----

    @Test
    public void violationMessage_startsWithIdentityRefusalPrefix() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone("+27812345678");
        try {
            StoreScopeValidator.validateCallerPhone("+27811111111");
            fail("Expected CallerIdentityViolationException");
        } catch (CallerIdentityViolationException e) {
            assertEquals(
                "IDENTITY_REFUSAL: I can only assist with queries for your own account. " +
                "For privacy and security reasons, I am not authorised to access another person's information. " +
                "Please contact iZinga support if you need further help.",
                e.getMessage());
        }
    }
}
