package io.curiousoft.ijudi.ordermanagement.model;

import javax.validation.constraints.NotBlank;

public class UserProfile extends Profile {

    private String idNumber;

    public UserProfile(@NotBlank(message = "profile name not valid") String name,
                       @NotBlank(message = "profile address not valid") String address,
                       @NotBlank(message = "profile image url not valid") String imageUrl,
                       @NotBlank(message = "profile mobile number not valid") String mobileNumber,
                       @NotBlank(message = "role not valid") String role) {
        super(name, address, imageUrl, mobileNumber, role);
        this.idNumber = idNumber;
    }


    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }
}
