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

    private const val VIOLATION_MESSAGE =
        "IDENTITY_REFUSAL: I can only assist with queries for your own account. " +
        "For privacy and security reasons, I am not authorised to access another person's information. " +
        "Please contact iZinga support if you need further help."

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
     * ADR-024: Validate that the supplied phone belongs to the authenticated caller.
     *
     * Normalizes both phones to their last 9 digits.
     * For CUSTOMER and DRIVER audiences:
     *   - If callerPhone in context is null → HARD REJECT (no pass-through)
     *   - If callerPhone present but does not match the supplied phone → throw
     * For STORE audience (and null audience): no-op.
     *
     * @throws CallerIdentityViolationException if the phones do not match or caller is unidentified
     */
    @JvmStatic
    fun validateCallerPhone(suppliedPhone: String?) {
        val audience = CallerIdentityContext.getAudience()
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
     * ADR-024: Validate that the supplied ID belongs to the authenticated caller.
     *
     * The caller's own ID is resolved lazily via [callerIdResolver] to keep this
     * validator free of a direct repo dependency.
     *
     * Enforcement applies for CUSTOMER and DRIVER audiences only.
     * STORE audience is not subject to this check.
     *
     * @throws CallerIdentityViolationException if the id does not match the caller's own id
     */
    @JvmStatic
    fun validateCallerOwnsId(id: String?, callerIdResolver: Supplier<String?>) {
        val audience = CallerIdentityContext.getAudience()
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
