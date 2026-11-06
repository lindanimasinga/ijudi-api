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
 *
 * SEC-MCP-01: [IS_MCP_REQUEST] is set to true by [StoreScopeValidationFilter] at the start
 * of every /mcp request, regardless of whether a valid scope token is present. It is the
 * authoritative sentinel for "this invocation is on the MCP surface". It is NEVER settable
 * from a JWT claim or request parameter — only the filter may call [setMcpRequest].
 */
object CallerIdentityContext {

    private val CALLER_PHONE: ThreadLocal<String?> = ThreadLocal()
    /** String form of the audience (e.g. "CUSTOMER", "DRIVER", "STORE") — not the typed enum. */
    private val AUDIENCE: ThreadLocal<String?> = ThreadLocal()
    /**
     * SEC-MCP-01: true when the current thread is serving a /mcp request.
     * Set unconditionally by StoreScopeValidationFilter at the start of doFilterInternal().
     * Never true on REST, scheduler, or event-listener paths (thread-local not set).
     */
    private val IS_MCP_REQUEST: ThreadLocal<Boolean?> = ThreadLocal()

    @JvmStatic fun setCallerPhone(callerPhone: String?) = CALLER_PHONE.set(callerPhone)
    @JvmStatic fun getCallerPhone(): String? = CALLER_PHONE.get()

    @JvmStatic fun setAudience(audience: String?) = AUDIENCE.set(audience)
    @JvmStatic fun getAudience(): String? = AUDIENCE.get()

    /**
     * SEC-MCP-01: Set the MCP-request flag. Only [StoreScopeValidationFilter] may call this.
     */
    @JvmStatic fun setMcpRequest(value: Boolean) = IS_MCP_REQUEST.set(value)

    /**
     * SEC-MCP-01: Returns true when the current thread is serving a /mcp request.
     * Returns false (not null) when the flag has not been set (REST/scheduler/event paths).
     */
    @JvmStatic fun isMcpRequest(): Boolean = IS_MCP_REQUEST.get() == true

    /**
     * Clear all ThreadLocals. Must be called in the filter's unconditional finally block.
     */
    @JvmStatic fun clear() {
        CALLER_PHONE.remove()
        AUDIENCE.remove()
        IS_MCP_REQUEST.remove()
    }
}
