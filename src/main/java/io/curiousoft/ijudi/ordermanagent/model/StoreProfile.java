package io.curiousoft.ijudi.ordermanagent.model;

import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StoreProfile extends Profile {

    @Indexed(unique = true)
    private String regNumber;
    private List<BusinessHours> businessHours;
    private boolean hasVat;
    private List<String> tags = new ArrayList<>();
    private boolean featured;
    private Date featuredExpiry;

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

    public boolean getHasVat() {
        return hasVat;
    }

    public void setHasVat(boolean hasVat) {
        this.hasVat = hasVat;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public boolean getFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public void setFeaturedExpiry(Date featuredExpiry) {
        this.featuredExpiry = featuredExpiry;
    }

    public Date getFeaturedExpiry() {
        return featuredExpiry;
    }
}
