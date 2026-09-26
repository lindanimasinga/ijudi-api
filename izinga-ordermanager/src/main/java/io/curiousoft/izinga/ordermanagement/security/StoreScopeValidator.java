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
}
