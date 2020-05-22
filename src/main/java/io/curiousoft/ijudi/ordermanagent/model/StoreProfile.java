package io.curiousoft.ijudi.ordermanagent.model;

import javax.validation.constraints.NotBlank;

public class StoreProfile extends Profile {

    private String regNumber;

    public StoreProfile(@NotBlank(message = "profile name not valid") String name,
                        @NotBlank(message = "profile address not valid") String address,
                        @NotBlank(message = "profile image url not valid") String imageUrl,
                        @NotBlank(message = "profile mobile number not valid") String mobileNumber,
                        @NotBlank(message = "role not valid") String role) {
        super(name, address, imageUrl, mobileNumber, role);
    }

    public String getRegNumber() {
        return regNumber;
    }

    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }
}
