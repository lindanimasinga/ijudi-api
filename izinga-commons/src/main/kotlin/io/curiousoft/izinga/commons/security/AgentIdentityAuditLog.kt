package io.curiousoft.izinga.commons.security

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.util.Date

/**
 * ADR-024: Immutable audit record written whenever [CallerIdentityViolationException]
 * is about to be thrown.
 *
 * Collection: [agent_identity_audit_log].
 * TTL: 90 days on [timestamp] (configured in MongoDB via a TTL index).
 *
 * Audit writes are [org.springframework.scheduling.annotation.Async]
 * (fire-and-forget). A write failure MUST NOT suppress the real
 * [CallerIdentityViolationException] being thrown to the caller.
 */
@Document(collection = "agent_identity_audit_log")
data class AgentIdentityAuditLog(
    @Id val id: String? = null,

    /** TTL index (90 days) applied via @Indexed on this field. */
    @Indexed val timestamp: Date = Date(),

    val callerPhone: String? = null,
    val audience: String? = null,
    val agentName: String? = null,
    val toolName: String? = null,
    val violationType: ViolationType? = null,

    /**
     * The blocked third-party phone/orderId/userId the AI agent attempted to access.
     */
    val suppliedIdentifier: String? = null,

    /** Nullable — may not be available on all code paths. */
    val conversationId: String? = null,
) {
    /** Violation type — machine-parseable enum for alerting dashboards. */
    enum class ViolationType {
        PHONE_MISMATCH,
        ORDER_NOT_OWNED,
        PAYOUT_NOT_OWNED,
        THIRD_PARTY_REGISTRATION,
        ID_NOT_OWNED
    }
}
