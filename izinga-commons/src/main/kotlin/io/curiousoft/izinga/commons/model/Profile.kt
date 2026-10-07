package io.curiousoft.izinga.commons.model

import com.fasterxml.jackson.annotation.JsonIgnore
import io.curiousoft.izinga.commons.validator.ValidMobileNumber
import org.springframework.data.mongodb.core.index.Indexed
import jakarta.validation.constraints.NotBlank

open class Profile(
    var name: @NotBlank(message = "profile name not valid") String?,
    var address: @NotBlank(message = "profile address not valid") String?,
    var imageUrl: @NotBlank(message = "profile image url not valid") String?,
    @field:ValidMobileNumber(message = "profile mobile not format is not valid. Please put like +27812815577 or 27812815577")
    @field:Indexed(unique = true)
    @param:ValidMobileNumber(message = "profile mobile number not valid") var mobileNumber: @NotBlank(message = "profile mobile not format is not valid. Please put like +27812815577 or 27812815577") String?,
    // role is deliberately nullable. A null role is the first-class signal for an OTP-verified
    // placeholder profile that has not yet completed signup (see WhatsAppOtpService.createUserProfile()).
    // The constraint that role MUST be present for a fully-registered user lives in
    // UserProfileService.validateUserProfileForCreate(), applied only on the POST /user path.
    // Do NOT add @NotNull here: if the use-site-target bug in this file's annotations is ever
    // fixed globally (switching bare annotations to @field:/@param: targets), restoring @NotNull
    // on this field would silently break the OTP placeholder creation path.
    var role: ProfileRoles?
) : BaseModel() {
    var description: String? = null
    var yearsInService = 0
    var likes = 0
    var serviceType = StoreType.MOVERS
    @set:JsonIgnore
    var servicesCompleted = 0

    @set:JsonIgnore
    var badges = 0
    var emailAddress: String? = null

    @set:JsonIgnore
    var responseTimeMinutes = 0
    var bank: Bank? = null
    var latitude = 0.0
    var longitude = 0.0
    var documents: Set<DocumentAttachment>? = null
    var availabilityStatus: ProfileAvailabilityStatus = ProfileAvailabilityStatus.OFFLINE
    var profileApproved = false
    var profileApprovedDate: String? = null
}

enum class ProfileAvailabilityStatus {
   ONLINE, OFFLINE, AWAY
}