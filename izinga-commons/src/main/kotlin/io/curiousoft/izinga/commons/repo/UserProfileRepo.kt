package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile

interface UserProfileRepo : ProfileRepo<UserProfile> {
    fun findByMobileNumber(phone: String): UserProfile?
    fun findByRoleAndLatitudeBetweenAndLongitudeBetween(
        messenger: ProfileRoles,
        latMin: Double,
        latMax: Double,
        longMin: Double,
        longMax: Double
    ): List<UserProfile>?

    fun findByIdIn(inactiveCustomers45Days: MutableSet<String>): MutableList<UserProfile>
    fun findByProfileApproved(bool: Boolean): List<UserProfile>
    fun findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
        role: ProfileRoles,
        serviceType: StoreType,
        minLat: Double,
        maxLat: Double,
        minLong: Double,
        maxLong: Double
    ): List<UserProfile>?
}