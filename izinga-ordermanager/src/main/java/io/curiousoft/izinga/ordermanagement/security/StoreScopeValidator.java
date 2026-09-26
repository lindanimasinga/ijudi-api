package io.curiousoft.izinga.ordermanagement.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Helper called by store-scoped MCP {@code @Tool} methods to enforce that the tool
 * is operating on the permitted store from the request's scope token.
 *
 * SA-021-8: each store-reading @Tool method must call {@code StoreScopeValidator.validate(storeId)}.
 *
 * Behaviour:
 * - If the scope token's permittedStoreId is null (no scope token or DRIVER/CUSTOMER audience),
 *   the call is permitted without restriction (backward compatible for driver/customer agents).
 * - If permittedStoreId is non-null and equals storeId (null-safe), the call is permitted.
 * - If permittedStoreId is non-null and does NOT match, throws {@link StoreScopeViolationException}.
 */
public final class StoreScopeValidator {

    private static final Logger LOG = LoggerFactory.getLogger(StoreScopeValidator.class);

    private StoreScopeValidator() { /* utility class */ }

    /**
     * Validate that the requested {@code storeId} matches the permitted store scope.
     *
     * @param storeId the storeId argument passed to the @Tool method
     * @throws StoreScopeViolationException if the scope is set and does not match
     */
    public static void validate(String storeId) {
        String permitted = StoreScopeContext.getPermittedStoreId();
        if (permitted == null) {
            // No scope restriction — driver/customer agents or unauthenticated MCP calls
            // (existing backward-compatible behaviour; security teams accept this per SEC finding 6)
            return;
        }
        if (!Objects.equals(permitted, storeId)) {
            LOG.warn("StoreScopeViolation: permittedStoreId={} requestedStoreId={}", permitted, storeId);
            throw new StoreScopeViolationException(
                    "Store scope violation: access to store '" + storeId + "' is not permitted by this token");
        }
    }

    /**
     * ADR-021 Decision 1 Extension: validate that the current request's audience claim
     * matches one of the {@code permitted} values.
     *
     * <p>Unlike {@link #validate(String)}, a null audience is NOT a pass-through — it is
     * rejected immediately (reject-by-default). This ensures unauthenticated or no-token
     * callers cannot invoke audience-restricted {@code @Tool} methods.
     *
     * <p>SA-021-16 / SEC-WA02-01-E: both permittedStoreId and audience ThreadLocals are
     * managed by {@link StoreScopeContext}; both are cleared together in the filter's
     * {@code finally} block.
     *
     * @param permitted one or more audience values that are allowed to call this tool
     * @throws AudienceViolationException if the caller's audience is absent or not in {@code permitted}
     */
    public static void validateAudience(String... permitted) {
        String caller = StoreScopeContext.getAudience();
        if (caller == null) {
            // No JWT present — reject-by-default for all audience-restricted tools
            LOG.warn("AudienceViolation: no audience claim in scope token — rejecting audience-restricted tool call");
            throw new AudienceViolationException(
                    "Audience required but no scope token is present on this request");
        }
        for (String allowed : permitted) {
            if (allowed.equals(caller)) {
                return;
            }
        }
        LOG.warn("AudienceViolation: callerAudience={} requiredAudiences={}", caller, java.util.Arrays.toString(permitted));
        throw new AudienceViolationException(
                "Audience violation: caller audience '" + caller + "' is not in permitted set " +
                        java.util.Arrays.toString(permitted));
    }
}
