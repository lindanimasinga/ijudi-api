package io.curiousoft.izinga.commons.security

/**
 * ADR-024: ThreadLocal holder for caller-identity data extracted from the scope JWT.
 *
 * Companion to [io.curiousoft.izinga.ordermanagement.security.StoreScopeContext] in
 * izinga-ordermanager, which holds the typed [Audience] and storeId scoping state.
 * This class holds only the identity-enforcement state (callerPhone + string audience)
 * so it can be imported by izinga-commons-dependent modules (izinga-usermanagement,
 * izinga-recon, etc.) that do not have access to izinga-ordermanager.
 *
 * [StoreScopeValidationFilter] in izinga-ordermanager populates both classes.
 * [clear] must be called in an unconditional finally block (same rule as StoreScopeContext).
 */
object CallerIdentityContext {

    private val CALLER_PHONE: ThreadLocal<String?> = ThreadLocal()
    /** String form of the audience (e.g. "CUSTOMER", "DRIVER", "STORE") — not the typed enum. */
    private val AUDIENCE: ThreadLocal<String?> = ThreadLocal()

    @JvmStatic fun setCallerPhone(callerPhone: String?) = CALLER_PHONE.set(callerPhone)
    @JvmStatic fun getCallerPhone(): String? = CALLER_PHONE.get()

    @JvmStatic fun setAudience(audience: String?) = AUDIENCE.set(audience)
    @JvmStatic fun getAudience(): String? = AUDIENCE.get()

    /**
     * Clear all ThreadLocals. Must be called in the filter's unconditional finally block.
     */
    @JvmStatic fun clear() {
        CALLER_PHONE.remove()
        AUDIENCE.remove()
    }
}
