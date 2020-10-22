package io.curiousoft.ijudi.ordermanagement.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class UserProfile extends Profile {

    private String idNumber;
    private SignUpReason signUpReason;

    public UserProfile(@NotBlank(message = "profile name not valid") String name,
                       @NotNull(message = "signupReason not valid") SignUpReason signUpReason,
                       @NotBlank(message = "profile address not valid") String address,
                       @NotBlank(message = "profile image url not valid") String imageUrl,
                       @NotBlank(message = "profile mobile number not valid") String mobileNumber,
                       @NotNull(message = "role not valid") ProfileRoles role) {
        super(name, address, imageUrl, mobileNumber, role);
        this.idNumber = idNumber;
        this.signUpReason = signUpReason;
    }


    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public SignUpReason getSignUpReason() {
        return signUpReason;
    }

    public void setSignUpReason(SignUpReason signUpReason) {
        this.signUpReason = signUpReason;
    }

    public enum SignUpReason {
        DELIVERY_DRIVER,
        SELL,
        BUY
    }
}
