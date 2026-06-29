package io.curiousoft.izinga.usermanagement.users

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "documentUploadSession")
data class DocumentUploadSession(
    @Id
    val id: String,
    val userId: String,
    val fieldName: String,
    val originalFileName: String,
    val mimeType: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    var consumedAt: Instant? = null,
)
