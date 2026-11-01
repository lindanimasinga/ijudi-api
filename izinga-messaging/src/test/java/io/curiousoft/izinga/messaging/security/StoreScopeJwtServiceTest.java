package io.curiousoft.izinga.messaging.security;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-024: Tests that the callerPhone claim is correctly embedded in and extracted from
 * the scope JWT, while existing claims remain unchanged.
 *
 * Uses JUnit 5 to match the izinga-messaging module's test style.
 */
@ExtendWith(MockitoExtension.class)
class StoreScopeJwtServiceTest {

    private StoreScopeJwtService service;

    @BeforeEach
    void setUp() {
        // Must be at least 32 chars for HMAC256
        service = new StoreScopeJwtService("test-secret-at-least-32-chars-1234");
    }

    // ---- generateScopeToken with callerPhone ----

    @Test
    void generateScopeToken_withCallerPhone_embedsCallerPhoneClaim() {
        String token = service.generateScopeToken("store-abc", Audience.STORE, "+27812345678");
        Map<String, String> claims = service.validateAndExtract(token);
        assertEquals("+27812345678", claims.get("callerPhone"));
        assertEquals("store-abc", claims.get("storeId"));
        assertEquals("STORE", claims.get("audience"));
    }

    @Test
    void generateScopeToken_withNullCallerPhone_callerPhoneClaimIsNull() {
        String token = service.generateScopeToken("store-abc", Audience.STORE, null);
        Map<String, String> claims = service.validateAndExtract(token);
        assertNull(claims.get("callerPhone"));
    }

    @Test
    void generateScopeToken_twoArgBackwardCompatOverload_callerPhoneClaimIsNull() {
        String token = service.generateScopeToken(null, Audience.CUSTOMER);
        Map<String, String> claims = service.validateAndExtract(token);
        assertNull(claims.get("callerPhone"), "Backward-compat overload must produce null callerPhone");
        assertEquals("CUSTOMER", claims.get("audience"));
    }

    @Test
    void generateScopeToken_customerAudience_withCallerPhone() {
        String token = service.generateScopeToken(null, Audience.CUSTOMER, "+27811111111");
        Map<String, String> claims = service.validateAndExtract(token);
        assertEquals("+27811111111", claims.get("callerPhone"));
        assertNull(claims.get("storeId"));
        assertEquals("CUSTOMER", claims.get("audience"));
    }

    // ---- buildScopedUrl with callerPhone ----

    @Test
    void buildScopedUrl_withCallerPhone_urlContainsScopeParam() {
        String url = service.buildScopedUrl("https://api.izinga.co.za/mcp", null, Audience.CUSTOMER, "+27812345678");
        assertTrue(url.startsWith("https://api.izinga.co.za/mcp?scope="), "URL must contain ?scope=");

        // Extract and validate the embedded token
        String token = url.substring(url.indexOf("scope=") + 6);
        Map<String, String> claims = service.validateAndExtract(token);
        assertEquals("+27812345678", claims.get("callerPhone"));
        assertEquals("CUSTOMER", claims.get("audience"));
    }

    @Test
    void buildScopedUrl_twoArgOverload_callerPhoneIsNull() {
        String url = service.buildScopedUrl("https://api.izinga.co.za/mcp", null, Audience.CUSTOMER);
        String token = url.substring(url.indexOf("scope=") + 6);
        Map<String, String> claims = service.validateAndExtract(token);
        assertNull(claims.get("callerPhone"));
    }

    @Test
    void buildScopedUrl_existingQueryParam_appendsWithAmpersand() {
        String url = service.buildScopedUrl("https://api.izinga.co.za/mcp?foo=bar", "store-xyz", Audience.STORE, null);
        assertTrue(url.contains("&scope="), "Must append scope with & when ? already exists");
    }

    // ---- existing claims unaffected ----

    @Test
    void existingClaims_storeIdAndAudience_unaffectedByCallerPhone() {
        String token = service.generateScopeToken("store-xyz", Audience.DRIVER, "+27820000001");
        Map<String, String> claims = service.validateAndExtract(token);
        assertEquals("store-xyz", claims.get("storeId"));
        assertEquals("DRIVER", claims.get("audience"));
        assertEquals("+27820000001", claims.get("callerPhone"));
    }
}
