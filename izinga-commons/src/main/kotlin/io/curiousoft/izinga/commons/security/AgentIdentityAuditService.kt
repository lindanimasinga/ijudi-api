package io.curiousoft.izinga.commons.security

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID

/**
 * ADR-024: Asynchronous service that writes [AgentIdentityAuditLog] records
 * whenever a caller-identity violation is detected.
 *
 * Contract:
 * - [recordViolation] is @Async — it fires and forgets.
 * - A Mongo write failure is caught, logged at ERROR, and NEVER rethrown.
 * - A write failure must NOT suppress the [CallerIdentityViolationException]
 *   already being thrown to the caller.
 * - A structured WARN-level log line tagged [MCP-IDENTITY-VIOLATION] is always
 *   emitted alongside the Mongo write for log-based alerting pipelines.
 */
@Service
class AgentIdentityAuditService(
    private val repository: AgentIdentityAuditLogRepository,
) {
    private val log = LoggerFactory.getLogger(AgentIdentityAuditService::class.java)

    /**
     * Write an audit record for a caller identity violation.
     *
     * This method is @Async — runs on a Spring-managed task executor thread
     * and returns immediately from the caller's perspective. Exceptions thrown here
     * are caught and logged at ERROR but never rethrown.
     *
     * FIX-04 (ADR-024 Legal C-2): [audience] is now an explicit parameter captured by the
     * CALLER on the request thread BEFORE invoking this @Async method.  The previous
     * implementation read [CallerIdentityContext.getAudience()] inside this method, which ran
     * on a Spring TaskExecutor thread where the request-thread ThreadLocal is not visible,
     * causing every audit record to be saved with audience = "UNKNOWN".
     *
     * @param callerPhone        the authenticated caller's phone from the scope JWT (may be null)
     * @param suppliedIdentifier the phone/orderId/userId the agent tried to access
     * @param violationType      enum discriminator for dashboards and alerting
     * @param toolName           the MCP @Tool method name (e.g. "find_user_by_phone")
     * @param audience           audience string captured on the REQUEST thread before async dispatch
     *                           (e.g. "CUSTOMER", "DRIVER", "STORE"); use "UNKNOWN" when absent
     * @param agentName          the AI agent name; may be null
     * @param conversationId     WhatsApp conversation/session ID; may be null
     */
    @Async
    fun recordViolation(
        callerPhone: String?,
        suppliedIdentifier: String?,
        violationType: AgentIdentityAuditLog.ViolationType,
        toolName: String,
        audience: String,
        agentName: String? = null,
        conversationId: String? = null,
    ) {
        // Belt-and-suspenders: structured WARN log line for log-pipeline alerting
        log.warn("[MCP-IDENTITY-VIOLATION] violationType={} toolName={} agentName={} audience={}",
            violationType, toolName, agentName, audience)
        try {
            val record = AgentIdentityAuditLog(
                id = UUID.randomUUID().toString(),
                timestamp = Date(),
                callerPhone = callerPhone,
                audience = audience,
                agentName = agentName,
                toolName = toolName,
                violationType = violationType,
                suppliedIdentifier = suppliedIdentifier,
                conversationId = conversationId,
            )
            repository.save(record)
            log.debug("AgentIdentityAudit: saved record id={} violationType={}", record.id, violationType)
        } catch (e: Exception) {
            // Audit write failure must never reach the caller.
            log.error("AgentIdentityAudit: FAILED to write audit record — violationType={} toolName={} error={}",
                violationType, toolName, e.message, e)
        }
    }
}
