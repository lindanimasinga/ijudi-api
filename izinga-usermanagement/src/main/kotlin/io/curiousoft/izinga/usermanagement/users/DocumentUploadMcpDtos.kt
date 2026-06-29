package io.curiousoft.izinga.usermanagement.users

data class DocumentFieldStatusDto(
    val name: String,
    val label: String,
    val required: Boolean,
    val uploaded: Boolean,
)

data class RequiredDocumentsForUserResponse(
    val userId: String?,
    val mobileNumber: String?,
    val serviceTypeLabel: String?,
    val missingFields: List<String>,
    val missingDocumentFields: List<String>,
    val documentFields: List<DocumentFieldStatusDto>,
)

data class UploadSessionInstructionsResponse(
    val sessionId: String,
    val fieldName: String,
    val uploadEndpoint: String,
    val metadataEnabled: Boolean,
    val acceptedMimeTypes: List<String>,
    val expiresAt: String,
)

data class AttachUploadedDocumentResponse(
    val success: Boolean,
    val userId: String?,
    val fieldName: String,
    val fileUrl: String,
    val remainingMissingFields: List<String>,
    val remainingMissingDocumentFields: List<String>,
)