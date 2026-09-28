package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;

/**
 * ThreadLocal holder for the per-request store scope extracted from the
 * X-Agent-Scope JWT (query param "scope") by {@link StoreScopeValidationFilter}.
 *
 * SEC-WA02-01-C/E: {@link #clear()} MUST be called in an unconditional finally block
 * in the filter to ensure no residual state leaks across requests on a reused thread.
 * Both fields are cleared unconditionally.
 *
 * SA-021-13 / NOTE-02: {@code AUDIENCE} is typed as {@code ThreadLocal<Audience>}
 * for compile-time safety, consistent with ADR-021 Decision 1 Extension.
 */
public final class StoreScopeContext {

    private StoreScopeContext() { /* utility class */ }

    private static final ThreadLocal<String>   PERMITTED_STORE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Audience> AUDIENCE           = new ThreadLocal<>();

    /**
     * Set the permitted store ID for the current request.
     * Only the filter may call this.
     */
    public static void setPermittedStoreId(String storeId) {
        PERMITTED_STORE_ID.set(storeId);
    }

    /**
     * Set the audience for the current request.
     * SA-021-13: typed as {@link Audience} for compile-time safety.
     * Only the filter may call this.
     */
    public static void setAudience(Audience audience) {
        AUDIENCE.set(audience);
    }

    /**
     * Get the permitted store ID for the current request.
     * Returns null when no token was presented (e.g. direct API call without scope).
     */
    public static String getPermittedStoreId() {
        return PERMITTED_STORE_ID.get();
    }

    /**
     * Get the audience for the current request.
     * SA-021-13: returns typed {@link Audience} for compile-time safety.
     */
    public static Audience getAudience() {
        return AUDIENCE.get();
    }

    /**
     * SEC-WA02-01-C/E: unconditionally clear BOTH fields.
     * MUST be called in the filter's finally block.
     */
    public static void clear() {
        PERMITTED_STORE_ID.remove();
        AUDIENCE.remove();
    }
}
