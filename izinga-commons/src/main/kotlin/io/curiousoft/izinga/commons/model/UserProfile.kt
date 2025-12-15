package io.curiousoft.izinga.commons.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.Date
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

class UserProfile(
    name: @NotBlank(message = "profile name not valid") String?,
    var signUpReason: @NotNull(message = "signupReason not valid") SignUpReason?,
    address: @NotBlank(message = "profile address not valid") String?,
    imageUrl: @NotBlank(message = "profile image url not valid") String?,
    mobileNumber: @NotBlank(message = "profile mobile number not valid") String?,
    role: @NotNull(message = "role not valid") ProfileRoles?) : Profile(name, address, imageUrl, mobileNumber, role) {

    @set:JsonIgnore
    var isPermanentEmployed: Boolean? = null

    var idNumber: String? = null
    var dateOfBirth: String? = null
    var termsAccepted: Boolean? = null
    var termsAcceptedDate: Date? = null

    enum class SignUpReason {
        DELIVERY_DRIVER, SELL, BUY, LICENSING
    }
}