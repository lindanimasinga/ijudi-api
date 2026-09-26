package io.curiousoft.izinga.messaging.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * SEC-WA02-01-B: JWT service for per-request MCP tool scoping tokens.
 *
 * Uses a DEDICATED secret {@code store-scope.jwt.secret}, completely separate from the
 * Firebase user-facing JWT secret. Never derived from or shared with Firebase auth.
 *
 * SA-021-17: agent generates a short-lived (5-min) JWT with storeId + audience claims
 * and appends it as a query parameter (?scope=<token>) to the MCP server URL so OpenAI
 * forwards it on every tool-invocation request.
 *
 * SEC-WA02-01-D: audience in the JWT is sourced from AiAgentConfig.audience (MongoDB),
 * never from the inbound message or any request payload.
 *
 * SEC-WA02-01-A: query-parameter transport is used instead of a request header because
 * OpenAI Responses API header forwarding behaviour has not been empirically verified.
 */
@Service
public class StoreScopeJwtService {

    private static final Logger LOG = LoggerFactory.getLogger(StoreScopeJwtService.class);
    private static final String CLAIM_STORE_ID = "storeId";
    private static final String CLAIM_AUDIENCE_KEY = "audience";
    /** Token lifetime. Short-lived: one request cycle. */
    private static final long TOKEN_TTL_MINUTES = 5L;
    /** Query parameter name that the filter reads from incoming /mcp requests. */
    public static final String SCOPE_PARAM = "scope";

    private final Algorithm algorithm;

    /**
     * SEC-WA02-01-B: dedicated secret — NOT derived from Firebase or any other secret.
     * Must be at least 32 chars of entropy. Configure via {@code store-scope.jwt.secret}
     * in application.properties / Secrets Manager.
     */
    public StoreScopeJwtService(@Value("${store-scope.jwt.secret}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    /**
     * Generate a scoped JWT embedding {@code storeId} and {@code audience}.
     *
     * @param storeId  the store this token is scoped to; may be null for non-STORE audiences
     * @param audience the audience sourced from AiAgentConfig (SEC-WA02-01-D)
     * @return signed JWT string
     */
    public String generateScopeToken(String storeId, Audience audience) {
        var now = Instant.now();
        var builder = JWT.create()
                .withIssuer("izinga-agent-scope")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES)))
                .withClaim(CLAIM_AUDIENCE_KEY, audience != null ? audience.name() : null);
        if (storeId != null) {
            builder = builder.withClaim(CLAIM_STORE_ID, storeId);
        }
        String token = builder.sign(algorithm);
        LOG.debug("Generated scope token for storeId={} audience={}", storeId, audience);
        return token;
    }

    /**
     * Validate the token and return its claims.
     *
     * @param token  JWT from request query parameter
     * @return map with "storeId" (nullable) and "audience" (nullable string)
     * @throws JWTVerificationException if the token is invalid or expired
     */
    public Map<String, String> validateAndExtract(String token) {
        DecodedJWT decoded = JWT.require(algorithm)
                .withIssuer("izinga-agent-scope")
                .build()
                .verify(token);
        Map<String, String> claims = new HashMap<>();
        var storeIdClaim = decoded.getClaim(CLAIM_STORE_ID);
        claims.put(CLAIM_STORE_ID, storeIdClaim.isNull() ? null : storeIdClaim.asString());
        var audienceClaim = decoded.getClaim(CLAIM_AUDIENCE_KEY);
        claims.put(CLAIM_AUDIENCE_KEY, audienceClaim.isNull() ? null : audienceClaim.asString());
        return claims;
    }

    /**
     * Append the scope token as a query parameter to the given MCP server URL.
     *
     * SA-021-17 / SEC-WA02-01-A: query-parameter transport.
     *
     * @param baseUrl  the MCP server base URL
     * @param storeId  store scope (may be null for non-STORE agents)
     * @param audience sourced from AiAgentConfig.audience
     * @return URL with {@code ?scope=<jwt>} appended
     */
    public String buildScopedUrl(String baseUrl, String storeId, Audience audience) {
        String token = generateScopeToken(storeId, audience);
        if (baseUrl.contains("?")) {
            return baseUrl + "&" + SCOPE_PARAM + "=" + token;
        }
        return baseUrl + "?" + SCOPE_PARAM + "=" + token;
    }
}
