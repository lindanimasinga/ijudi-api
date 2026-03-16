package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserProfileService(userProfileRepo: UserProfileRepo) : ProfileServiceImpl<UserProfileRepo, UserProfile>(userProfileRepo) {
    
    fun findUserByPhone(phone: String): UserProfile? {
        val last9Digits = phone.substring(phone.length - 9)
        return listOf("0", "+27", "27")
            .firstNotNullOfOrNull { profileRepo.findByMobileNumber("$it$last9Digits") }
    }

    fun findByLocation(role: ProfileRoles, latitude: Double, longitude: Double, range: Double): List<UserProfile>? {
        val maxLong = longitude + range
        val minLong = longitude - range
        val maxLat = latitude + range
        val minLat = latitude - range
        return profileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(
            role, minLat,
            maxLat, minLong, maxLong
        )
    }

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
        return mobileNumberFormmatted
    }
}