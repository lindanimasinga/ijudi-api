package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.userconfig.FieldSpec
import io.curiousoft.izinga.usermanagement.userconfig.UserConfig
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import lombok.extern.slf4j.Slf4j
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.stream.Collectors
import java.util.stream.Stream

@Service
class UserProfileService(val userProfileRepo: UserProfileRepo, val eventPublisher: ApplicationEventPublisher, userConfigService: UserConfigService) : ProfileServiceImpl<UserProfileRepo, UserProfile>(userProfileRepo, eventPublisher) {

    private lateinit var userConfig: MutableList<UserConfig?>

    init {
        userConfig = userConfigService.findAll().toMutableList()
    }

    @Tool(name = "find_user_by_phone", description = "Finds a user profile by phone number. It will try to find the userby adding different country code prefixes to the phone number provided.")
    fun findUserByPhone(phone: String): UserProfile? {
        val last9Digits = phone.substring(phone.length - 9)
        return listOf("0", "+27", "27")
            .firstNotNullOfOrNull { profileRepo.findByMobileNumber("$it$last9Digits") }
    }

    @Tool(name = "find_users", description = "finds users by role and location. It will return a list of user profiles that match the specified role and are within the specified range of the given latitude and longitude.")
    fun findByLocation(@ToolParam(description = "This will always have a value MESSENGER because this serach is only allow to search drivers available") role: ProfileRoles,
                       latitude: Double,
                       longitude: Double,
                       @ToolParam(description = "range is in degrees. 1 degree is approximately 111 km. So a range of 0.01 would be approximately 1.11 km") range: Double,
                       storeType: StoreType): List<UserProfile>? {
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

    fun findMessengersByAdminId(messengerAdminId: String): List<UserProfile> {
        return profileRepo.findByRoleAndMessengerAdminId(ProfileRoles.MESSENGER, messengerAdminId)
    }

    private fun fomatMobileNumber(mobileNumber: String): String {
        val last9Digits = mobileNumber.substring(mobileNumber.length - 9)
        var mobileNumberFormmatted = last9Digits.replace("\\s".toRegex(), "")
        mobileNumberFormmatted = mobileNumberFormmatted.replace("-", "")
        return "+27$mobileNumberFormmatted"
    }

    fun getAllMissingFields(profile: UserProfile): List<String> {
        val missingAddress = getMissingFields(profile)
        val missingFields = getMissingMandatoryFields(profile, userConfig)
        missingFields.addAll(missingAddress)
        return missingFields
    }

    @Tool(name = "get_missing_fields_by_phone", description = "Returns a list of missing mandatory field names for the user profile associated with the given mobile number.")
    fun getAllMissingFields(mobileNumber: String): List<String> {
        return findUserByPhone(mobileNumber)?.let {
            getAllMissingFields(it)
        } ?: emptyList()
    }

    /**
     * Returns a list of missing mandatory field names for the given profile and userConfig.
     */
    private fun getMissingMandatoryFields(
        profile: UserProfile?,
        userConfig: MutableList<UserConfig?>?
    ): MutableList<String> {
        if (profile == null || userConfig == null) return mutableListOf<String>()
        return userConfig.stream()
            .filter { it: UserConfig? -> it!!.label == profile.description }
            .flatMap<FieldSpec?> { config: UserConfig? -> Stream.of<FieldSpec?>(*config!!.mandatoryFields.toTypedArray<FieldSpec?>()) }
            .filter { it: FieldSpec? -> profile.tag == null || profile.tag.get(it!!.name) == null }
            .map<String>(FieldSpec::name)
            .collect(Collectors.toList())
    }

    private fun getMissingFields(userProfile: UserProfile): MutableList<String> {
        val missingFields: MutableList<String> = ArrayList<String>()
        if (userProfile.description == null || userProfile.description!!.isBlank()) {
            missingFields.add("description")
        }
        if (userProfile.address == null || userProfile.address!!.isBlank()) {
            missingFields.add("address")
        }
        if (userProfile.latitude == 0.0 || userProfile.longitude == 0.0) {
            missingFields.add("geo coordinates")
        }
        if (userProfile.name == null || userProfile.name!!.isBlank()) {
            missingFields.add("name")
        }
        if (userProfile.imageUrl == null || userProfile.imageUrl!!.isBlank()) {
            missingFields.add("Profile Picture")
        }

        // ...add other checks as needed...
        return missingFields
    }

}