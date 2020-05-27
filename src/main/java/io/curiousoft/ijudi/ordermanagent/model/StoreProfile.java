package io.curiousoft.ijudi.ordermanagent.model;

import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.NotBlank;
import java.util.List;

public class StoreProfile extends Profile {

    @Indexed(unique = true)
    private String regNumber;
    private List<BusinessHours> businessHours;

    public StoreProfile(@NotBlank(message = "profile name not valid") String name,
                        @NotBlank(message = "profile address not valid") String address,
                        @NotBlank(message = "profile image url not valid") String imageUrl,
                        @NotBlank(message = "profile mobile number not valid") String mobileNumber,
                        @NotBlank(message = "role not valid") String role,
                        @NotBlank(message = "Business hours not valid") List<BusinessHours> businessHours) {
        super(name, address, imageUrl, mobileNumber, role);
        this.businessHours = businessHours;
    }

    public String getRegNumber() {
        return regNumber;
    }

    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }

    public List<BusinessHours> getBusinessHours() {
        return businessHours;
    }

    public void setBusinessHours(List<BusinessHours> businessHours) {
        this.businessHours = businessHours;
    }
}
