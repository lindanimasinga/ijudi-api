package io.curiousoft.izinga.commons.security

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * ADR-024: Spring Data Mongo repository for [AgentIdentityAuditLog].
 *
 * Writes are always performed via [AgentIdentityAuditService.recordViolation]
 * which is @Async — never call this repository directly from tool methods.
 */
@Repository
interface AgentIdentityAuditLogRepository : MongoRepository<AgentIdentityAuditLog, String>
