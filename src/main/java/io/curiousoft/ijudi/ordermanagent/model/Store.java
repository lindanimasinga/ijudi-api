package io.curiousoft.ijudi.ordermanagent.model;

import javax.validation.constraints.NotBlank;

public class Store extends Profile {

    private String regNumber;

    public Store(String id, String regiNumber, @NotBlank(message = "profile name not valid") String name,
                 @NotBlank(message = "profile address not valid") String address,
                 @NotBlank(message = "profile image url not valid") String imageUrl,
                 @NotBlank(message = "profile mobile number not valid") String mobileNumber,
                 @NotBlank(message = "role not valid") String role) {
        super(name, address, imageUrl, mobileNumber, role);
        this.regNumber = regiNumber;
    }

    public String getRegNumber() {
        return regNumber;
    }

    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }
}
