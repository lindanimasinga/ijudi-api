package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Service
import org.springframework.context.ApplicationEventPublisher
import org.springframework.beans.factory.annotation.Autowired

@Service
class UserProfileService @Autowired constructor(userProfileRepo: UserProfileRepo, eventPublisher: ApplicationEventPublisher)
    : ProfileServiceImpl<UserProfileRepo, UserProfile>(userProfileRepo, eventPublisher) {

     @Tool(name = "find_user_by_phone", description = "Finds a user profile by phone number. It will try to find the userby adding different country code prefixes to the phone number provided.")
    fun findUserByPhone(phone: String): UserProfile? {
        val last9Digits = phone.substring(phone.length - 9)
        return listOf("0", "+27", "27")
            .firstNotNullOfOrNull { profileRepo.findByMobileNumber("$it$last9Digits") }
    }

    fun findByLocation(role: ProfileRoles, latitude: Double, longitude: Double, range: Double, storeType: StoreType): List<UserProfile>? {
        val maxLong = longitude + range
        val minLong = longitude - range
        val maxLat = latitude + range
        val minLat = latitude - range
        return profileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(role, storeType, minLat,
            maxLat, minLong, maxLong
        )
    }

    @Tool(name = "create_user", description = "Creates a new user profile. It can be a normal customer or a driver or a store owner depending on the role specified in the profile object.")
    @Throws(Exception::class)
    override fun create(profile: UserProfile): UserProfile {
        //remove empty spaces and dashes from the mobile number
        profile.mobileNumber = fomatMobileNumber(profile.mobileNumber!!)
        if (profileRepo.existsByMobileNumber(profile.mobileNumber!!)) throw Exception("User with phone number " + profile.mobileNumber + " already exist.")
        return super.create(profile)
    }

    fun pendingAproval(): List<UserProfile> {
        return profileRepo.findByProfileApproved(false)
    }

    private fun fomatMobileNumber(mobileNumber: String): String {
        val last9Digits = mobileNumber.substring(mobileNumber.length - 9)
        var mobileNumberFormmatted = last9Digits.replace("\\s".toRegex(), "")
        mobileNumberFormmatted = mobileNumberFormmatted.replace("-", "")
        return "+27$mobileNumberFormmatted"
    }
}