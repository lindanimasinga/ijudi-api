package io.curiousoft.izinga.usermanagement.users

import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface DocumentUploadSessionRepo : MongoRepository<DocumentUploadSession, String> {
    fun findFirstByUserIdAndFieldNameAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
        userId: String,
        fieldName: String,
        now: Instant,
    ): DocumentUploadSession?
}
