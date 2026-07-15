package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.IcaAcceptanceLog
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import io.curiousoft.izinga.usermanagement.userconfig.FieldSpec
import io.curiousoft.izinga.usermanagement.userconfig.UserConfig
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.Date
import java.util.stream.Collectors
import java.util.stream.Stream

@Service
class UserProfileService(
    val userProfileRepo: UserProfileRepo,
    val eventPublisher: ApplicationEventPublisher,
    userConfigService: UserConfigService,
    private val icaAcceptanceLogRepo: IcaAcceptanceLogRepo,
    private val referralCodeService: ReferralCodeService
) : ProfileServiceImpl<UserProfileRepo, UserProfile>(userProfileRepo, eventPublisher) {

    private val log = LoggerFactory.getLogger(UserProfileService::class.java)
    private lateinit var userConfig: MutableList<UserConfig?>

    init {
        userConfig = userConfigService.findAll().toMutableList()
    }

    @Tool(name = "find_user_by_phone", description = "Finds a user profile by phone number. It will try to find the userby adding different country code prefixes to the phone number provided.")
    fun findUserByPhone(phone: String): UserProfile? {
        if (phone.length < 9) {
            return null
        }
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

    /**
     * RP-004a: Entry point called from UserController when a `ref` query param is present.
     * Resolves the referral code to a REFERRAL_PARTNER and sets [UserProfile.referredByPartnerId]
     * before delegating to the standard create flow.
     *
     * @param referralCode raw referral code string from the `ref` query param; null = no attribution.
     */
    @Throws(Exception::class)
    fun create(profile: UserProfile, referralCode: String?): UserProfile {
        if (!referralCode.isNullOrBlank()) {
            val partner = referralCodeService.resolveCode(referralCode)
            if (partner != null) {
                log.info("Referral code {} resolved to partnerId={} for new user mobileNumber={}",
                    referralCode, partner.id, profile.mobileNumber)
                profile.referredByPartnerId = partner.id
            } else {
                log.warn("Referral code {} could not be resolved — no attribution set for mobileNumber={}",
                    referralCode, profile.mobileNumber)
            }
        }
        return create(profile)
    }

    @Tool(name = "create_user", description = "Creates a new user profile. It can be a normal customer or a driver or a store owner depending on the role specified in the profile object.")
    @Throws(Exception::class)
    override fun create(profile: UserProfile): UserProfile {
        //remove empty spaces and dashes from the mobile number
        profile.mobileNumber = fomatMobileNumber(profile.mobileNumber!!)
        if (profileRepo.existsByMobileNumber(profile.mobileNumber!!)) throw Exception("User with phone number " + profile.mobileNumber + " already exist.")

        // T-09: validate ambassadorId if provided; clear it if not a valid active ambassador
        val requestedAmbassadorId = profile.ambassadorId
        if (!requestedAmbassadorId.isNullOrBlank()) {
            val ambassador = profileRepo.findById(requestedAmbassadorId).orElse(null)
            if (ambassador != null && ambassador.role == ProfileRoles.AMBASSADOR && ambassador.profileApproved == true) {
                log.info("Valid ambassador referral: ambassadorId={} for new user mobileNumber={}", requestedAmbassadorId, profile.mobileNumber)
                profile.ambassadorId = requestedAmbassadorId
            } else {
                log.warn("Invalid or inactive ambassador referral: ambassadorId={} — setting to null for mobileNumber={}", requestedAmbassadorId, profile.mobileNumber)
                profile.ambassadorId = null
            }
        }

        return super.create(profile)
    }

    @Throws(Exception::class)
    override fun update(profileId: String, profile: UserProfile): UserProfile {
        val persisted = userProfileRepo.findById(profileId).orElse(null)
        val wasIcaAccepted = persisted?.icaAccepted == true
        val updated = super.update(profileId, profile)
        if (!wasIcaAccepted && updated.icaAccepted == true) {
            val logEntry = IcaAcceptanceLog(
                userId = updated.id ?: profileId,
                mobileNumber = updated.mobileNumber ?: "",
                icaVersion = updated.icaVersion ?: "unknown",
                acceptedAt = updated.icaAcceptedDate ?: Date()
            )
            try {
                icaAcceptanceLogRepo.insert(logEntry)
                log.info("ICA acceptance logged for userId={} icaVersion={}", logEntry.userId, logEntry.icaVersion)
            } catch (e: Exception) {
                log.error("Failed to write ICA acceptance log for userId={}", profileId, e)
            }
        }
        return updated
    }

    fun pendingAproval(): List<UserProfile> {
        return profileRepo.findByProfileApproved(false)
    }

    fun findMessengersByAdminId(messengerAdminId: String): List<UserProfile> {
        return profileRepo.findByRoleAndMessengerAdminId(ProfileRoles.MESSENGER, messengerAdminId)
    }

    fun findMessengersByLocation(latitude: Double, longitude: Double, range: Double): List<UserProfile>? {
        val maxLat = latitude + range
        val minLat = latitude - range
        val maxLng = longitude + range
        val minLng = longitude - range
        return profileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(
            ProfileRoles.MESSENGER, minLat, maxLat, minLng, maxLng
        )
    }

    private fun fomatMobileNumber(mobileNumber: String): String {
        require(mobileNumber.length >= 9) { "Invalid mobile number" }
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