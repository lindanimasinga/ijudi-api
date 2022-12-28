package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.Profile
import io.curiousoft.izinga.commons.model.ProfileRoles
import org.springframework.data.mongodb.repository.MongoRepository

interface ProfileRepo<U : Profile?> : MongoRepository<U, String?> {
    fun existsByMobileNumber(id: String?): Boolean
    fun findByRole(role: ProfileRoles?): List<U>?
}