package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SEC-MCP-01: verifies that {@link StoreScopeValidationFilter} sets and clears
 * the IS_MCP_REQUEST ThreadLocal flag correctly on every /mcp request.
 *
 * Key invariants:
 * - IS_MCP_REQUEST is true inside the filter chain (observed via a capturing FilterChain)
 * - IS_MCP_REQUEST is false after the filter completes (cleared in finally block)
 * - IS_MCP_REQUEST is false after the filter completes even when the downstream throws
 * - IS_MCP_REQUEST is false when token parsing fails (invalid token still sets flag)
 * - Non-/mcp paths skip the filter entirely — IS_MCP_REQUEST remains false
 */
@ExtendWith(MockitoExtension.class)
public class StoreScopeValidationFilterTest {

    @Mock private StoreScopeJwtService jwtService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private StoreScopeValidationFilter filter;

    @BeforeEach
    public void setUp() {
        filter = new StoreScopeValidationFilter(jwtService);
        StoreScopeContext.clear();
    }

    @AfterEach
    public void tearDown() {
        StoreScopeContext.clear();
    }

    // ── IS_MCP_REQUEST is set during the filter chain and cleared after ─────────

    @Test
    public void doFilterInternal_setsMcpRequestFlagDuringChain() throws Exception {
        when(request.getParameter(StoreScopeJwtService.SCOPE_PARAM)).thenReturn(null);

        AtomicBoolean flagDuringChain = new AtomicBoolean(false);

        FilterChain capturingChain = (req, res) -> {
            flagDuringChain.set(CallerIdentityContext.isMcpRequest());
        };

        filter.doFilterInternal(request, response, capturingChain);

        assertTrue(flagDuringChain.get(), "IS_MCP_REQUEST must be true inside the filter chain");
    }

    @Test
    public void doFilterInternal_clearsMcpRequestFlagAfterCompletion() throws Exception {
        when(request.getParameter(StoreScopeJwtService.SCOPE_PARAM)).thenReturn(null);

        FilterChain noopChain = (req, res) -> {};
        filter.doFilterInternal(request, response, noopChain);

        assertFalse(CallerIdentityContext.isMcpRequest(),
                "IS_MCP_REQUEST must be false after the filter completes");
    }

    @Test
    public void doFilterInternal_clearsMcpRequestFlagWhenDownstreamThrows() throws Exception {
        when(request.getParameter(StoreScopeJwtService.SCOPE_PARAM)).thenReturn(null);

        FilterChain throwingChain = (req, res) -> {
            throw new RuntimeException("simulated downstream failure");
        };

        try {
            filter.doFilterInternal(request, response, throwingChain);
            fail("Expected RuntimeException to propagate");
        } catch (RuntimeException e) {
            // expected — verify cleanup still happened
        }

        assertFalse(CallerIdentityContext.isMcpRequest(),
                "IS_MCP_REQUEST must be cleared even when downstream throws");
    }

    // ── Valid token: audience and callerPhone propagated ─────────────────────

    @Test
    public void doFilterInternal_validToken_populatesContextDuringChain() throws Exception {
        String fakeToken = "fake-jwt-token";
        when(request.getParameter(StoreScopeJwtService.SCOPE_PARAM)).thenReturn(fakeToken);

        Map<String, String> claims = new HashMap<>();
        claims.put("storeId", "store-abc");
        claims.put("audience", "STORE");
        claims.put("callerPhone", "+27812345678");
        when(jwtService.validateAndExtract(fakeToken)).thenReturn(claims);

        AtomicBoolean mcpFlagDuringChain = new AtomicBoolean(false);
        AtomicBoolean audienceDuringChain = new AtomicBoolean(false);

        FilterChain capturingChain = (req, res) -> {
            mcpFlagDuringChain.set(CallerIdentityContext.isMcpRequest());
            audienceDuringChain.set("STORE".equals(CallerIdentityContext.getAudience()));
        };

        filter.doFilterInternal(request, response, capturingChain);

        assertTrue(mcpFlagDuringChain.get(),
                "IS_MCP_REQUEST must be true inside chain (valid token)");
        assertTrue(audienceDuringChain.get(),
                "STORE audience must be set in context during chain");
        assertFalse(CallerIdentityContext.isMcpRequest());
    }

    // ── Invalid token: IS_MCP_REQUEST still set (null audience), cleared after ──

    @Test
    public void doFilterInternal_invalidToken_mcpFlagStillSetDuringChain() throws Exception {
        String badToken = "invalid-jwt";
        when(request.getParameter(StoreScopeJwtService.SCOPE_PARAM)).thenReturn(badToken);
        when(jwtService.validateAndExtract(badToken)).thenThrow(new RuntimeException("JWT parse error"));

        AtomicBoolean flagDuringChain = new AtomicBoolean(false);
        FilterChain capturingChain = (req, res) -> flagDuringChain.set(CallerIdentityContext.isMcpRequest());

        filter.doFilterInternal(request, response, capturingChain);

        assertTrue(flagDuringChain.get(),
                "IS_MCP_REQUEST must be true during chain even with invalid token");
        assertFalse(CallerIdentityContext.isMcpRequest(),
                "IS_MCP_REQUEST must be cleared after filter completes");
    }

    // ── FIX-01 / AMD-01 (REQ-A10): ADMIN token end-to-end through real JWT service ───

    /**
     * FIX-01 (AMD-01 Code Review): uses a REAL StoreScopeJwtService (test secret) to mint an
     * ADMIN token, so the test covers the full JWT → filter → context path rather than a mocked
     * claim map.  Assertions mirror doFilterInternal_validToken_populatesContextDuringChain but
     * for ADMIN audience:
     *   - StoreScopeContext.getAudience() == Audience.ADMIN during chain
     *   - CallerIdentityContext.getAudience() == "ADMIN" during chain (set by StoreScopeContext.setAudience)
     *   - CallerIdentityContext.isMcpRequest() is true during chain
     *   - StoreScopeContext.getPermittedStoreId() is null (ADMIN has no storeId)
     *   - All three are cleared after the filter returns
     */
    @Test
    public void doFilterInternal_adminToken_populatesAdminContextDuringChainAndClearsAfter() throws Exception {
        StoreScopeJwtService realJwtService = new StoreScopeJwtService("test-secret-at-least-32-chars-1234");
        StoreScopeValidationFilter adminFilter = new StoreScopeValidationFilter(realJwtService);

        String adminToken = realJwtService.generateScopeToken(null, Audience.ADMIN, "+27811111111");
        when(request.getParameter(StoreScopeJwtService.SCOPE_PARAM)).thenReturn(adminToken);

        AtomicBoolean adminAudienceDuringChain = new AtomicBoolean(false);
        AtomicBoolean callerAudienceAdminDuringChain = new AtomicBoolean(false);
        AtomicBoolean mcpFlagDuringChain = new AtomicBoolean(false);
        AtomicBoolean permittedStoreIdNullDuringChain = new AtomicBoolean(false);

        FilterChain capturingChain = (req, res) -> {
            adminAudienceDuringChain.set(StoreScopeContext.getAudience() == Audience.ADMIN);
            callerAudienceAdminDuringChain.set("ADMIN".equals(CallerIdentityContext.getAudience()));
            mcpFlagDuringChain.set(CallerIdentityContext.isMcpRequest());
            permittedStoreIdNullDuringChain.set(StoreScopeContext.getPermittedStoreId() == null);
        };

        adminFilter.doFilterInternal(request, response, capturingChain);

        assertTrue(adminAudienceDuringChain.get(),
                "StoreScopeContext.getAudience() must be ADMIN during chain");
        assertTrue(callerAudienceAdminDuringChain.get(),
                "CallerIdentityContext.getAudience() must be \"ADMIN\" during chain");
        assertTrue(mcpFlagDuringChain.get(),
                "CallerIdentityContext.isMcpRequest() must be true during chain");
        assertTrue(permittedStoreIdNullDuringChain.get(),
                "permittedStoreId must be null for ADMIN token (no storeId claim)");

        // After filter — all must be cleared
        assertNull(StoreScopeContext.getAudience(),
                "StoreScopeContext.getAudience() must be null after filter");
        assertNull(CallerIdentityContext.getAudience(),
                "CallerIdentityContext.getAudience() must be null after filter");
        assertFalse(CallerIdentityContext.isMcpRequest(),
                "CallerIdentityContext.isMcpRequest() must be false after filter");
    }

    // ── shouldNotFilter: non-/mcp paths skip the filter ───────────────────────

    @Test
    public void shouldNotFilter_nonMcpPath_returnsTrue() throws Exception {
        when(request.getRequestURI()).thenReturn("/user/profile");
        assertTrue(filter.shouldNotFilter(request), "Non-/mcp path must not be filtered");
    }

    @Test
    public void shouldNotFilter_mcpPath_returnsFalse() throws Exception {
        when(request.getRequestURI()).thenReturn("/mcp/sse");
        assertFalse(filter.shouldNotFilter(request), "MCP path must be filtered");
    }

    @Test
    public void shouldNotFilter_mcpSubPath_returnsFalse() throws Exception {
        when(request.getRequestURI()).thenReturn("/mcp/tools/list");
        assertFalse(filter.shouldNotFilter(request), "MCP sub-path must be filtered");
    }
}
