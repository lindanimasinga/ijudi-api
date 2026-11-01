package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService
import io.curiousoft.izinga.commons.security.CallerIdentityContext
import io.curiousoft.izinga.commons.security.CallerIdentityValidator
import io.curiousoft.izinga.usermanagement.userconfig.FieldDataType
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class DocumentUploadMcpService(
    private val userProfileService: UserProfileService,
    private val userConfigService: UserConfigService,
    private val documentUploadSessionRepo: DocumentUploadSessionRepo,
    /** ADR-024: injected for async audit writes on identity violations. */
    private val agentIdentityAuditService: AgentIdentityAuditService,
) {

    private val allowedMimeTypes = listOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "application/pdf",
    )

    private val allowedWhatsappHosts = setOf(
        "lookaside.fbsbx.com",
        "mmg.whatsapp.net",
        "graph.facebook.com",
    )

    @Tool(
        name = "get_required_documents_for_user",
        description = "Returns required document fields and missing fields for the user linked to a phone number."
    )
    fun getRequiredDocumentsForUser(
        @ToolParam(description = "User phone number, for example +27812815577") phone: String,
    ): RequiredDocumentsForUserResponse {
        // ADR-024: enforce caller identity before any data is returned
        enforceCallerPhoneOrAudit(phone, "get_required_documents_for_user")
        val normalizedPhone = validatePhoneInput(phone)
        val user = userProfileService.findUserByPhone(normalizedPhone)
            ?: throw IllegalArgumentException("No user profile found for phone number: $phone")
        return buildRequiredDocumentsResponse(user.mobileNumber ?: normalizedPhone, user.id)
    }

    @Tool(
        name = "create_document_upload_session",
        description = "Creates upload instructions for a required document field before linking it to a user profile."
    )
    fun createDocumentUploadSession(
        @ToolParam(description = "User phone number, for example +27812815577") phone: String,
        @ToolParam(description = "Document field name from user config, for example identityDocument") fieldName: String,
        @ToolParam(description = "Original file name, for example id-card.jpg") originalFileName: String,
        @ToolParam(description = "MIME type, for example image/jpeg or application/pdf") mimeType: String,
    ): UploadSessionInstructionsResponse {
        // ADR-024: enforce caller identity before any session is created
        enforceCallerPhoneOrAudit(phone, "create_document_upload_session")
        val normalizedPhone = validatePhoneInput(phone)
        val user = userProfileService.findUserByPhone(normalizedPhone)
            ?: throw IllegalArgumentException("No user profile found for phone number: $phone")
        val userId = user.id ?: throw IllegalArgumentException("User profile id is missing")

        require(originalFileName.isNotBlank()) { "originalFileName is required" }
        require(allowedMimeTypes.contains(mimeType.lowercase())) {
            "Unsupported mimeType: $mimeType. Allowed values are ${allowedMimeTypes.joinToString(", ")}"
        }
        validateDocumentField(user.mobileNumber ?: normalizedPhone, fieldName)

        val session = documentUploadSessionRepo.save(
            DocumentUploadSession(
                id = UUID.randomUUID().toString(),
                userId = userId,
                fieldName = fieldName,
                originalFileName = originalFileName,
                mimeType = mimeType.lowercase(),
                createdAt = Instant.now(),
                expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES),
            )
        )

        return UploadSessionInstructionsResponse(
            sessionId = session.id,
            fieldName = fieldName,
            uploadEndpoint = "/document?metadata=false",
            metadataEnabled = false,
            acceptedMimeTypes = allowedMimeTypes,
            expiresAt = session.expiresAt.toString(),
        )
    }

    @Tool(
        name = "attach_uploaded_document_to_user_tag",
        description = "Attaches an uploaded document URL to user.tag[fieldName], then returns remaining missing fields."
    )
    fun attachUploadedDocumentToUserTag(
        @ToolParam(description = "User phone number, for example +27812815577") phone: String,
        @ToolParam(description = "Document field name from user config, for example identityDocument") fieldName: String,
        @ToolParam(description = "Uploaded file public URL") fileUrl: String,
    ): AttachUploadedDocumentResponse {
        // ADR-024: enforce caller identity before attaching a document
        enforceCallerPhoneOrAudit(phone, "attach_uploaded_document_to_user_tag")
        val normalizedPhone = validatePhoneInput(phone)
        val user = userProfileService.findUserByPhone(normalizedPhone)
            ?: throw IllegalArgumentException("No user profile found for phone number: $phone")
        val userId = user.id ?: throw IllegalArgumentException("User profile id is missing")

        require(fileUrl.isNotBlank()) { "fileUrl is required" }
        require(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            "fileUrl must be an absolute http(s) URL"
        }
        validateDocumentField(user.mobileNumber ?: normalizedPhone, fieldName)

        val activeSession = documentUploadSessionRepo
            .findFirstByUserIdAndFieldNameAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                userId,
                fieldName,
                Instant.now()
            )

        if (activeSession == null && !isTrustedWhatsappMediaUrl(fileUrl)) {
            throw IllegalArgumentException(
                "No active upload session found for field '$fieldName'. Create a new upload session first or provide a WhatsApp media URL."
            )
        }

        // Preserve existing tag keys and update only the requested field.
        user.tag[fieldName] = fileUrl
        userProfileService.update(userId, user)

        if (activeSession != null) {
            activeSession.consumedAt = Instant.now()
            documentUploadSessionRepo.save(activeSession)
        }

        val result = buildRequiredDocumentsResponse(user.mobileNumber ?: normalizedPhone, userId)
        return AttachUploadedDocumentResponse(
            success = true,
            userId = userId,
            fieldName = fieldName,
            fileUrl = fileUrl,
            remainingMissingFields = result.missingFields,
            remainingMissingDocumentFields = result.missingDocumentFields,
        )
    }

    @Tool(
        name = "recheck_required_documents",
        description = "Rechecks required fields and required document fields after uploads."
    )
    fun recheckRequiredDocuments(
        @ToolParam(description = "User phone number, for example +27812815577") phone: String,
    ): RequiredDocumentsForUserResponse {
        // NOTE-01: call enforceCallerPhoneOrAudit directly with the CORRECT tool name so audit
        // records are attributed to "recheck_required_documents" not "get_required_documents_for_user".
        enforceCallerPhoneOrAudit(phone, "recheck_required_documents")
        val normalizedPhone = validatePhoneInput(phone)
        val user = userProfileService.findUserByPhone(normalizedPhone)
            ?: throw IllegalArgumentException("No user profile found for phone number: $phone")
        return buildRequiredDocumentsResponse(user.mobileNumber ?: normalizedPhone, user.id)
    }

    private fun buildRequiredDocumentsResponse(phone: String, userId: String?): RequiredDocumentsForUserResponse {
        val user = userProfileService.findUserByPhone(phone)
            ?: throw IllegalArgumentException("No user profile found for phone number: $phone")

        val config = userConfigService.findAll().firstOrNull { it.label == user.description }
            ?: throw IllegalArgumentException("No user config found for service type: ${user.description}")
        val missingFields = userProfileService.getAllMissingFields(phone)

        val mandatoryDocFields = config.mandatoryFields
            .filter { it.dataType == FieldDataType.DOCUMENT_URL }
        val optionalDocFields = config.optionalFields
            .filter { it.dataType == FieldDataType.DOCUMENT_URL }
        val allDocFields = mandatoryDocFields + optionalDocFields

        val missingDocumentFields = missingFields.filter { missingField ->
            allDocFields.any { it.name == missingField }
        }

        val documentStatuses = allDocFields.map { field ->
            val value = user.tag[field.name]
            DocumentFieldStatusDto(
                name = field.name,
                label = field.label,
                required = mandatoryDocFields.any { it.name == field.name },
                uploaded = !value.isNullOrBlank(),
            )
        }

        return RequiredDocumentsForUserResponse(
            userId = userId,
            mobileNumber = user.mobileNumber,
            serviceTypeLabel = user.description,
            missingFields = missingFields,
            missingDocumentFields = missingDocumentFields,
            documentFields = documentStatuses,
        )
    }

    private fun validateDocumentField(phone: String, fieldName: String) {
        val user = userProfileService.findUserByPhone(phone)
            ?: throw IllegalArgumentException("No user profile found for phone number: $phone")
        val config = userConfigService.findAll().firstOrNull { it.label == user.description }
            ?: throw IllegalArgumentException("No user config found for service type: ${user.description}")

        val documentFields = (config.mandatoryFields + config.optionalFields)
            .filter { it.dataType == FieldDataType.DOCUMENT_URL }

        if (documentFields.none { it.name == fieldName }) {
            throw IllegalArgumentException(
                "Field '$fieldName' is not a document field for service type '${user.description}'"
            )
        }
    }

    private fun validatePhoneInput(phone: String): String {
        val trimmed = phone.trim()
        require(trimmed.length >= 9) { "phone must contain at least 9 digits/characters" }
        return trimmed
    }

    private fun isTrustedWhatsappMediaUrl(fileUrl: String): Boolean {
        return try {
            val host = URI(fileUrl).host?.lowercase() ?: return false
            allowedWhatsappHosts.any { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * ADR-024: Validates that the supplied phone belongs to the JWT caller.
     * Writes an async audit record on violation and re-throws so the @Tool method
     * surfaces the refusal message to the AI agent.
     *
     * NOTE-02: [agentName] is now a parameter (defaulting to null) instead of being
     * hardcoded to "driver_support" so tools that are not exclusively for drivers can
     * supply a more accurate agent name, and existing callers need no change.
     *
     * FIX-04: [audience] is captured here on the REQUEST thread before the @Async dispatch.
     */
    private fun enforceCallerPhoneOrAudit(phone: String, toolName: String, agentName: String? = null) {
        try {
            CallerIdentityValidator.validateCallerPhone(phone)
        } catch (e: io.curiousoft.izinga.commons.security.CallerIdentityViolationException) {
            // FIX-04: capture audience on the request thread before the @Async dispatch
            val auditAudience = CallerIdentityContext.getAudience() ?: "UNKNOWN"
            agentIdentityAuditService.recordViolation(
                callerPhone = CallerIdentityContext.getCallerPhone(),
                suppliedIdentifier = phone,
                violationType = AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH,
                toolName = toolName,
                audience = auditAudience,
                agentName = agentName,
                conversationId = null
            )
            throw e
        }
    }
}