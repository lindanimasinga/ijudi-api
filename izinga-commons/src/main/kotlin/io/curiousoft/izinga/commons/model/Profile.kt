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
    var availabilityStatus: ProfileAvailabilityStatus = ProfileAvailabilityStatus.ONLINE
    /** Broad service category label for this profile (e.g. "Delivery", "Food", "Salon"). */
    var serviceType: String? = null
    /** Document URLs or storage keys associated with this profile (e.g. ID copy, business licence). */
    var documents: MutableList<String>? = null
    /** Whether this profile has been approved by an iZinga admin. CRITICAL: never reset by a PATCH — see StoreService.mergeNonNullFields. */
    var profileApproved: Boolean = false
    /** Date on which the profile was approved; null until first approval. */
    var profileApprovedDate: java.util.Date? = null
}

enum class ProfileAvailabilityStatus {
   ONLINE, OFFLINE, AWAY
}