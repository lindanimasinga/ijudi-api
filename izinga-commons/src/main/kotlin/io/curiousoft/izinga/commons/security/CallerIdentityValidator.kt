package io.curiousoft.izinga.commons.security

import org.slf4j.LoggerFactory
import java.util.function.Supplier

/**
 * ADR-024: Static utility for caller-identity enforcement on MCP [Tool] methods.
 *
 * Lives in izinga-commons so it is accessible from all downstream modules
 * (izinga-usermanagement, izinga-recon, izinga-ordermanager, etc.).
 *
 * Audience check uses the String-based [CallerIdentityContext.getAudience] (e.g. "CUSTOMER",
 * "DRIVER") rather than the typed Audience enum from izinga-messaging, keeping commons free of
 * a transitive dependency on the messaging module.
 *
 * Phone normalization uses the same last-9-digits logic extracted from
 * OrderServiceImpl.findOrderByPhone() per ADR-024.
 */
object CallerIdentityValidator {

    private val LOG = LoggerFactory.getLogger(CallerIdentityValidator::class.java)

    private const val CUSTOMER = "CUSTOMER"
    private const val DRIVER = "DRIVER"
    /** AMD-01 (REQ-A10): ADMIN audience passes all phone/id validators. */
    private const val ADMIN = "ADMIN"

    private const val VIOLATION_MESSAGE =
        "IDENTITY_REFUSAL: I can only assist with queries for your own account. " +
        "For privacy and security reasons, I am not authorised to access another person's information. " +
        "Please contact iZinga support if you need further help."

    private const val MCP_UNAUTHENTICATED_MESSAGE =
        "IDENTITY_REFUSAL: This tool requires an authenticated scope token. " +
        "Unauthenticated or tokenless requests are not permitted on the MCP surface. " +
        "Please contact iZinga support if you need further help."

    /**
     * SEC-MCP-01 / SEC-MCP-02: Validate that the current request's audience is one of
     * the [permitted] values.
     *
     * This method is a NO-OP when [CallerIdentityContext.isMcpRequest] is false
     * (REST controllers, schedulers, event listeners are unaffected).
     *
     * On MCP requests:
     * - Null audience → HARD-REJECT (tokenless call)
     * - Audience not in [permitted] → HARD-REJECT
     *
     * Designed for modules (izinga-usermanagement, izinga-recon) that do NOT have
     * access to [io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator]
     * from izinga-ordermanager.
     *
     * @param permitted one or more audience strings (e.g. "STORE", "CUSTOMER", "DRIVER")
     * @throws CallerIdentityViolationException if audience is absent or not in [permitted]
     */
    @JvmStatic
    fun validateMcpAudience(vararg permitted: String) {
        if (!CallerIdentityContext.isMcpRequest()) return
        val audience = CallerIdentityContext.getAudience()
        if (audience == null) {
            LOG.warn("[MCP-IDENTITY-VIOLATION] HARD-REJECT: MCP request has no scope token (null audience) on validateMcpAudience permittedAudiences={}", permitted.contentToString())
            throw CallerIdentityViolationException(MCP_UNAUTHENTICATED_MESSAGE)
        }
        if (!permitted.contains(audience)) {
            LOG.warn("[MCP-IDENTITY-VIOLATION] AUDIENCE_VIOLATION: audience={} permittedAudiences={}", audience, permitted.contentToString())
            throw CallerIdentityViolationException(VIOLATION_MESSAGE)
        }
    }

    /**
     * SEC-MCP-BUG-01 / FIX-05: Reject a tokenless MCP request as the FIRST statement
     * of a @Tool method, before any input-dependent branch or repository access that
     * could produce a distinguishable response (e.g. null, empty list, or a validation
     * error message rather than IDENTITY_REFUSAL).
     *
     * This is a NO-OP when [CallerIdentityContext.isMcpRequest] is false (REST controllers,
     * schedulers, event listeners are unaffected).
     *
     * @param toolName the MCP tool name (e.g. "find_user_by_phone") included in the log line
     * @throws CallerIdentityViolationException if this is an MCP request with null audience (tokenless)
     */
    @JvmStatic
    fun rejectTokenlessMcp(toolName: String) {
        if (CallerIdentityContext.isMcpRequest() && CallerIdentityContext.getAudience() == null) {
            LOG.warn("[MCP-IDENTITY-VIOLATION] HARD-REJECT: MCP tokenless request on tool={}", toolName)
            throw CallerIdentityViolationException(MCP_UNAUTHENTICATED_MESSAGE)
        }
    }

    /**
     * ADR-024: Normalize a phone number to its last 9 digits.
     * Extracted from OrderServiceImpl.findOrderByPhone() so both the validator
     * and service callers share the same normalization logic.
     *
     * @return last 9 digits, or null if input is null or shorter than 9 characters
     */
    @JvmStatic
    fun normalizeLast9(phone: String?): String? {
        if (phone == null || phone.length < 9) return null
        return phone.substring(phone.length - 9)
    }

    /**
     * ADR-024 / SEC-MCP-01: Validate that the supplied phone belongs to the authenticated caller.
     *
     * Normalizes both phones to their last 9 digits.
     * SEC-MCP-01: On MCP requests, a null audience is a hard reject (tokenless call).
     * For CUSTOMER and DRIVER audiences:
     *   - If callerPhone in context is null → HARD REJECT (no pass-through)
     *   - If callerPhone present but does not match the supplied phone → throw
     * For STORE audience (and null audience on non-MCP paths): no-op.
     *
     * @throws CallerIdentityViolationException if the phones do not match, caller is unidentified,
     *         or an MCP request has no scope token (null audience)
     */
    @JvmStatic
    fun validateCallerPhone(suppliedPhone: String?) {
        val audience = CallerIdentityContext.getAudience()
        // SEC-MCP-01: tokenless MCP request → hard reject regardless of audience value
        if (CallerIdentityContext.isMcpRequest() && audience == null) {
            LOG.warn("[MCP-IDENTITY-VIOLATION] HARD-REJECT: MCP request has no scope token (null audience) on validateCallerPhone")
            throw CallerIdentityViolationException(MCP_UNAUTHENTICATED_MESSAGE)
        }
        // AMD-01 (REQ-A10): ADMIN audience passes phone/id validators unconditionally.
        // This check is intentionally placed AFTER the null-audience reject above.
        if (audience == ADMIN) {
            LOG.debug("[MCP-ADMIN] pass-through on validateCallerPhone — ADMIN audience")
            return
        }
        if (audience != CUSTOMER && audience != DRIVER) return

        val callerPhone = CallerIdentityContext.getCallerPhone()
        if (callerPhone == null) {
            LOG.warn("[MCP-IDENTITY-VIOLATION] HARD-REJECT: audience={} but callerPhone claim is absent in scope token", audience)
            throw CallerIdentityViolationException(VIOLATION_MESSAGE)
        }
        val normalizedCaller = normalizeLast9(callerPhone)
        val normalizedSupplied = normalizeLast9(suppliedPhone)
        if (normalizedCaller == null || normalizedCaller != normalizedSupplied) {
            LOG.warn("[MCP-IDENTITY-VIOLATION] PHONE_MISMATCH: audience={}", audience)
            throw CallerIdentityViolationException(VIOLATION_MESSAGE)
        }
    }

    /**
     * ADR-024 / SEC-MCP-01: Validate that the supplied ID belongs to the authenticated caller.
     *
     * The caller's own ID is resolved lazily via [callerIdResolver] to keep this
     * validator free of a direct repo dependency.
     *
     * SEC-MCP-01: On MCP requests, a null audience is a hard reject (tokenless call).
     * Enforcement applies for CUSTOMER and DRIVER audiences only.
     * STORE audience is not subject to this check.
     *
     * @throws CallerIdentityViolationException if the id does not match the caller's own id,
     *         or an MCP request has no scope token (null audience)
     */
    @JvmStatic
    fun validateCallerOwnsId(id: String?, callerIdResolver: Supplier<String?>) {
        val audience = CallerIdentityContext.getAudience()
        // SEC-MCP-01: tokenless MCP request → hard reject regardless of audience value
        if (CallerIdentityContext.isMcpRequest() && audience == null) {
            LOG.warn("[MCP-IDENTITY-VIOLATION] HARD-REJECT: MCP request has no scope token (null audience) on validateCallerOwnsId")
            throw CallerIdentityViolationException(MCP_UNAUTHENTICATED_MESSAGE)
        }
        // AMD-01 (REQ-A10): ADMIN audience passes phone/id validators unconditionally.
        // This check is intentionally placed AFTER the null-audience reject above.
        if (audience == ADMIN) {
            LOG.debug("[MCP-ADMIN] pass-through on validateCallerOwnsId — ADMIN audience")
            return
        }
        if (audience != CUSTOMER && audience != DRIVER) return

        val callerId = callerIdResolver.get()
        // NOTE-03: a null resolved callerId is a hard reject regardless of what `id` is.
        // Previously `null != null` evaluated to false, allowing unresolved callers to pass
        // through when the order also had no assigned messenger (messengerId=null).
        if (callerId == null || callerId != id) {
            LOG.warn("[MCP-IDENTITY-VIOLATION] ID_NOT_OWNED: audience={}", audience)
            throw CallerIdentityViolationException(VIOLATION_MESSAGE)
        }
    }
}
