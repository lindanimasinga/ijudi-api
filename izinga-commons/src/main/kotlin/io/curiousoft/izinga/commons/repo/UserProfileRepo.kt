package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import java.util.*

interface UserProfileRepo : ProfileRepo<UserProfile> {
    fun findByMobileNumber(phone: String?): Optional<UserProfile>?
    fun findByRoleAndLatitudeBetweenAndLongitudeBetween(
        messenger: ProfileRoles,
        latMin: Double,
        latMax: Double,
        longMin: Double,
        longMax: Double
    ): List<UserProfile>?
}