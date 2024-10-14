package io.curiousoft.izinga.commons.model

import com.fasterxml.jackson.annotation.JsonIgnore
import io.curiousoft.izinga.commons.validator.ValidMobileNumber
import org.springframework.data.mongodb.core.index.Indexed
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

open class Profile(
    var name: @NotBlank(message = "profile name not valid") String?,
    var address: @NotBlank(message = "profile address not valid") String?,
    var imageUrl: @NotBlank(message = "profile image url not valid") String?,
    @field:ValidMobileNumber(message = "profile mobile not format is not valid. Please put like +27812815577 or 27812815577")
    @field:Indexed(unique = true)
    @param:ValidMobileNumber(message = "profile mobile number not valid") var mobileNumber: @NotBlank(message = "profile mobile not format is not valid. Please put like +27812815577 or 27812815577") String?,
    var role: @NotNull(message = "profile role not valid") ProfileRoles?
) : BaseModel() {
    var surname: String? = null
    var description: String? = null
    var yearsInService = 0
    var likes = 0

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
    var availabilityStatus: ProfileAvailabilityStatus = ProfileAvailabilityStatus.ONLINE
}

enum class ProfileAvailabilityStatus {
   ONLINE, OFFLINE, AWAY
}