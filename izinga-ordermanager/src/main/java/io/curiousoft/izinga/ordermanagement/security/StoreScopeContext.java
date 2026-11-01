package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;

/**
 * ThreadLocal holder for the per-request store scope extracted from the
 * X-Agent-Scope JWT (query param "scope") by {@link StoreScopeValidationFilter}.
 *
 * SEC-WA02-01-C/E: {@link #clear()} MUST be called in an unconditional finally block
 * in the filter to ensure no residual state leaks across requests on a reused thread.
 * All three fields are cleared unconditionally.
 *
 * SA-021-13 / NOTE-02: {@code AUDIENCE} is typed as {@code ThreadLocal<Audience>}
 * for compile-time safety, consistent with ADR-021 Decision 1 Extension.
 *
 * ADR-024: {@code CALLER_PHONE} holds the normalized phone number of the authenticated
 * WhatsApp caller extracted from the scope JWT's {@code callerPhone} claim.
 */
public final class StoreScopeContext {

    private StoreScopeContext() { /* utility class */ }

    private static final ThreadLocal<String>   PERMITTED_STORE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Audience> AUDIENCE           = new ThreadLocal<>();
    /** ADR-024: caller phone extracted from scope JWT claim {@code callerPhone}. */
    private static final ThreadLocal<String>   CALLER_PHONE       = new ThreadLocal<>();

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
     * ADR-024: also propagates to {@link CallerIdentityContext} as a String so
     * downstream modules (izinga-usermanagement, izinga-recon) can check it.
     * Only the filter may call this.
     */
    public static void setAudience(Audience audience) {
        AUDIENCE.set(audience);
        // ADR-024: propagate to commons-level context for cross-module access
        CallerIdentityContext.setAudience(audience != null ? audience.name() : null);
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
     * ADR-024: Set the caller phone for the current request.
     * Also propagates to {@link CallerIdentityContext} for cross-module access.
     * Only the filter may call this.
     */
    public static void setCallerPhone(String callerPhone) {
        CALLER_PHONE.set(callerPhone);
        // ADR-024: propagate to commons-level context
        CallerIdentityContext.setCallerPhone(callerPhone);
    }

    /**
     * ADR-024: Get the caller phone for the current request.
     * Returns null when no token was presented or the token lacked a callerPhone claim.
     */
    public static String getCallerPhone() {
        return CALLER_PHONE.get();
    }

    /**
     * ADR-024 / SEC-WA02-01-C/E: unconditionally clear ALL THREE fields,
     * and also clear the commons-level {@link CallerIdentityContext}.
     * MUST be called in the filter's finally block.
     */
    public static void clear() {
        PERMITTED_STORE_ID.remove();
        AUDIENCE.remove();
        CALLER_PHONE.remove();
        // ADR-024: also clear commons-level context so cross-module reads see null
        CallerIdentityContext.clear();
    }
}
