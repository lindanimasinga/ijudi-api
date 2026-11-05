package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.usermanagement.userconfig.FieldDataType
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
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

    fun getRequiredDocumentsForUser(
        phone: String,
    ): RequiredDocumentsForUserResponse {
        val normalizedPhone = validatePhoneInput(phone)
        val user = userProfileService.findUserByPhone(normalizedPhone)
            ?: throw IllegalArgumentException("No user profile found for phone number: $phone")
        return buildRequiredDocumentsResponse(user.mobileNumber ?: normalizedPhone, user.id)
    }

    fun createDocumentUploadSession(
        phone: String,
        fieldName: String,
        originalFileName: String,
        mimeType: String,
    ): UploadSessionInstructionsResponse {
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

    fun attachUploadedDocumentToUserTag(
        phone: String,
        fieldName: String,
        fileUrl: String,
    ): AttachUploadedDocumentResponse {
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

    fun recheckRequiredDocuments(
        phone: String,
    ): RequiredDocumentsForUserResponse {
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

}