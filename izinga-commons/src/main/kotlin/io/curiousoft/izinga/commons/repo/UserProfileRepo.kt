package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.repository.Query

interface UserProfileRepo : ProfileRepo<UserProfile> {
    fun findByMobileNumber(phone: String): UserProfile?
    @Query("{ 'role': ?0, 'tag.messengerAdminId': ?1 }")
    fun findByRoleAndMessengerAdminId(role: ProfileRoles, messengerAdminId: String): List<UserProfile>
    fun findByRoleAndLatitudeBetweenAndLongitudeBetween(
        messenger: ProfileRoles,
        latMin: Double,
        latMax: Double,
        longMin: Double,
        longMax: Double
    ): List<UserProfile>?

    fun findByIdIn(inactiveCustomers45Days: MutableSet<String>): MutableList<UserProfile>
    fun findByProfileApproved(bool: Boolean): List<UserProfile>
    fun findByAmbassadorId(ambassadorId: String): List<UserProfile>
    fun findByReferralCode(referralCode: String): UserProfile?

    /**
     * RP-010: Returns all UserProfile records where referredByPartnerId matches the given partnerId.
     * Supports pagination for the /referral-partner/me/referrals endpoint.
     * MongoDB index on referredByPartnerId is declared on the UserProfile document — see UserProfile.kt.
     */
    fun findByReferredByPartnerId(partnerId: String, pageable: Pageable): Page<UserProfile>

    /** Non-paginated variant used for summary counts. */
    fun findByReferredByPartnerId(partnerId: String): List<UserProfile>
    fun findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
        role: ProfileRoles,
        serviceType: StoreType,
        minLat: Double,
        maxLat: Double,
        minLong: Double,
        maxLong: Double
    ): List<UserProfile>?
}