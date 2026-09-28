package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

/**
 * AC-06 / AC-07 / AC-08: verifies MCP store-scope enforcement via StoreScopeValidator.
 *
 * AC-06: when permittedStoreId matches the requested storeId, the call is permitted.
 * AC-07: when permittedStoreId does NOT match, StoreScopeViolationException is thrown.
 * AC-08: prompt cannot bypass server-side enforcement — regardless of what the model
 *        sends as storeId, the ThreadLocal check rejects a wrong storeId.
 *
 * Also verifies the audience layer (ADR-021 Decision 1 Extension):
 * - null audience → AudienceViolationException (reject-by-default)
 * - matching audience → passes
 * - non-matching audience → AudienceViolationException
 */
@RunWith(MockitoJUnitRunner.class)
public class StoreScopedMcpToolTest {

    private static final String STORE_X = "store-x-permitted";
    private static final String STORE_Y = "store-y-unauthorized";

    @Before
    public void clearContextBefore() {
        StoreScopeContext.clear();
    }

    @After
    public void clearContextAfter() {
        StoreScopeContext.clear();
    }

    // ---- AC-06: permitted storeId passes ----

    @Test
    public void validate_permittedStoreId_matchingRequest_passes() {
        // Simulates the filter having set permittedStoreId = STORE_X on this request
        StoreScopeContext.setPermittedStoreId(STORE_X);

        // The @Tool method calls StoreScopeValidator.validate(STORE_X) — must NOT throw
        StoreScopeValidator.validate(STORE_X);
    }

    @Test
    public void validate_nullPermittedStoreId_noRestriction_passes() {
        // No scope token present — permittedStoreId null means no enforcement
        // (driver/customer agents behave this way)
        assertNull(StoreScopeContext.getPermittedStoreId());
        StoreScopeValidator.validate(STORE_X); // must not throw
        StoreScopeValidator.validate(STORE_Y); // must not throw
        StoreScopeValidator.validate(null);   // must not throw
    }

    // ---- AC-07: wrong storeId → STORE_SCOPE_VIOLATION ----

    @Test
    public void validate_permittedStoreX_requestedStoreY_throwsViolation() {
        StoreScopeContext.setPermittedStoreId(STORE_X);

        StoreScopeViolationException ex = null;
        try {
            StoreScopeValidator.validate(STORE_Y);
            fail("Expected StoreScopeViolationException");
        } catch (StoreScopeViolationException e) {
            ex = e;
        }
        assertNotNull(ex);
        // No store data (names, products, etc.) should be in the error message
        assertFalse("Error message must not contain the full storeY details",
                ex.getMessage().contains("businessHours") || ex.getMessage().contains("stockList"));
    }

    @Test
    public void validate_permittedStoreX_requestedNull_throwsViolation() {
        StoreScopeContext.setPermittedStoreId(STORE_X);

        try {
            StoreScopeValidator.validate(null);
            fail("Expected StoreScopeViolationException when storeId is null but scope is set");
        } catch (StoreScopeViolationException e) {
            // expected
        }
    }

    // ---- AC-08: prompt cannot bypass server-side enforcement ----

    /**
     * AC-08: even if the AI model is instructed by a crafted system prompt to "act as store Y"
     * and calls the tool with storeId=Y, the server-side ThreadLocal check rejects it
     * because the scope token carries permittedStoreId=X.
     *
     * This test simulates the actual enforcement path:
     * - scope token established permittedStoreId=X (done by the filter from the JWT)
     * - model sends storeId=Y (the "prompt bypass attempt")
     * - StoreScopeValidator.validate(Y) throws STORE_SCOPE_VIOLATION
     */
    @Test
    public void validate_promptBypassAttempt_permittedX_requestedY_rejected() {
        // Step 1: scope token has been set for store X by the filter
        StoreScopeContext.setPermittedStoreId(STORE_X);

        // Step 2: model sends storeId=Y (prompt injection causing wrong storeId)
        String modelSentStoreId = STORE_Y; // model was told "you are the agent for store Y"

        try {
            // Step 3: validate rejects it — the prompt cannot override the ThreadLocal
            StoreScopeValidator.validate(modelSentStoreId);
            fail("Prompt bypass must not succeed — server-side enforcement must reject storeId=Y when permitted=X");
        } catch (StoreScopeViolationException e) {
            // Pass: the server correctly rejected the attempted bypass
            assertTrue("Exception message must reference the violation",
                    e.getMessage().contains("scope violation") || e.getMessage().contains("not permitted"));
        }
    }

    // ---- Audience layer (ADR-021 Decision 1 Extension) ----

    @Test
    public void validateAudience_nullAudience_rejectsDefault() {
        // No scope token → audience is null → must reject for all audience-restricted tools
        assertNull(StoreScopeContext.getAudience());

        try {
            StoreScopeValidator.validateAudience(Audience.STORE);
            fail("Expected AudienceViolationException when audience is null");
        } catch (AudienceViolationException e) {
            // expected: reject-by-default
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void validateAudience_matchingAudience_passes() {
        StoreScopeContext.setAudience(Audience.STORE);

        StoreScopeValidator.validateAudience(Audience.STORE); // must not throw
    }

    @Test
    public void validateAudience_nonMatchingAudience_throwsViolation() {
        StoreScopeContext.setAudience(Audience.DRIVER);

        try {
            StoreScopeValidator.validateAudience(Audience.STORE);
            fail("Expected AudienceViolationException for DRIVER calling a STORE-only tool");
        } catch (AudienceViolationException e) {
            // expected
            // No store data in the error fields
            assertFalse("Audience violation must not leak store data",
                    e.getMessage().contains("storeMenu") || e.getMessage().contains("businessHours"));
        }
    }

    @Test
    public void validateAudience_customerAudience_rejectsStoreOnlyTool() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        try {
            StoreScopeValidator.validateAudience(Audience.STORE);
            fail("Expected AudienceViolationException for CUSTOMER calling a STORE-only tool");
        } catch (AudienceViolationException e) {
            // expected
        }
    }

    @Test
    public void validateAudience_multiplePermitted_matchingAudience_passes() {
        // NOTE-02: Audience enum does not have ADMIN — use DRIVER as the second permitted value
        // (tests that varargs still work with multiple Audience values)
        StoreScopeContext.setAudience(Audience.DRIVER);

        // A driver-or-store tool permits both
        StoreScopeValidator.validateAudience(Audience.STORE, Audience.DRIVER); // must not throw
    }
}
