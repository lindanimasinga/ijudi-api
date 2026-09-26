package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Servlet filter protecting /mcp/** endpoints.
 *
 * SEC-WA02-01-A: reads the agent-scope JWT from the "scope" query parameter
 * (query-parameter transport chosen because OpenAI header forwarding is unverified).
 *
 * SEC-WA02-01-C/E: {@link StoreScopeContext#clear()} is called in an UNCONDITIONAL
 * finally block — both permittedStoreId and audience are cleared regardless of whether
 * token parsing succeeds or fails.
 *
 * Note: /mcp/** is currently permitAll() in SecurityConfig (accepted risk, SEC finding 6).
 * This filter adds a soft-enforcement layer: if a valid scope token is present, it is
 * enforced; if absent, the filter passes through to allow backward-compatible tool calls
 * from DRIVER/CUSTOMER agents that may not yet generate scope tokens.
 */
@Component
public class StoreScopeValidationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(StoreScopeValidationFilter.class);
    private static final String MCP_PATH_PREFIX = "/mcp";

    private final StoreScopeJwtService jwtService;

    public StoreScopeValidationFilter(StoreScopeJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(MCP_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String scopeParam = request.getParameter(StoreScopeJwtService.SCOPE_PARAM);
        try {
            if (scopeParam != null && !scopeParam.isBlank()) {
                try {
                    Map<String, String> claims = jwtService.validateAndExtract(scopeParam);
                    String storeId = claims.get("storeId");
                    String audience = claims.get("audience");
                    StoreScopeContext.setPermittedStoreId(storeId);
                    StoreScopeContext.setAudience(audience);
                    LOG.debug("Scope token accepted: storeId={} audience={}", storeId, audience);
                } catch (Exception e) {
                    // Invalid token — log and allow filter chain to proceed without scope
                    // (soft enforcement; tool methods will still throw if they require scope)
                    LOG.warn("Invalid scope token on /mcp request — proceeding without scope: {}", e.getMessage());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // SEC-WA02-01-C/E: unconditionally clear both fields
            StoreScopeContext.clear();
        }
    }
}
