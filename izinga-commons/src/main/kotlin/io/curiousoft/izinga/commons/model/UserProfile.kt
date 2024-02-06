package io.curiousoft.izinga.commons.model

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

class UserProfile(
    name: @NotBlank(message = "profile name not valid") String?,
    var signUpReason: @NotNull(message = "signupReason not valid") SignUpReason?,
    address: @NotBlank(message = "profile address not valid") String?,
    imageUrl: @NotBlank(message = "profile image url not valid") String?,
    mobileNumber: @NotBlank(message = "profile mobile number not valid") String?,
    role: @NotNull(message = "role not valid") ProfileRoles?
) : Profile(name, address, imageUrl, mobileNumber, role) {
    var isPermanentEmployed: Boolean = false
    var idNumber: String? = null

    enum class SignUpReason {
        DELIVERY_DRIVER, SELL, BUY
    }
}