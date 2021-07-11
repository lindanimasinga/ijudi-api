package io.curiousoft.ijudi.ordermanagement.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import io.curiousoft.ijudi.ordermanagement.validator.ValidMobileNumber;
import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class Profile extends BaseModel {

    @NotBlank(message = "profile name not valid")
    private String name;
    private String surname;
    private String description;
    private int yearsInService;
    @NotBlank(message = "profile address not valid")
    private String address;
    @NotBlank(message = "profile image url not valid")
    private String imageUrl;
    private int likes;
    private int servicesCompleted;
    private int badges;
    @Indexed(unique = true)
    @NotBlank(message = "profile mobile number not valid")
    @ValidMobileNumber(message = "profile mobile not format is not valid. Please put like +27812815577 or 27812815577")
    private String mobileNumber;
    private String emailAddress;
    @NotNull(message = "role not valid")
    private ProfileRoles role;
    @JsonIgnore
    private int responseTimeMinutes;
    @JsonIgnore
    private Bank bank;
    private double latitude;
    private double longitude;


    public Profile(@NotBlank(message = "profile name not valid") String name,
                   @NotBlank(message = "profile address not valid") String address,
                   @NotBlank(message = "profile image url not valid") String imageUrl,
                   @NotBlank(message = "profile mobile not format is not valid. Please put like +27812815577 or 27812815577")
                   @ValidMobileNumber(message = "profile mobile number not valid") String mobileNumber,
                   @NotNull(message = "profile role not valid") ProfileRoles role) {
        super();
        this.name = name;
        this.address = address;
        this.imageUrl = imageUrl;
        this.mobileNumber = mobileNumber;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getYearsInService() {
        return yearsInService;
    }

    public void setYearsInService(int yearsInService) {
        this.yearsInService = yearsInService;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public int getServicesCompleted() {
        return servicesCompleted;
    }

    @JsonIgnore
    public void setServicesCompleted(int servicesCompleted) {
        this.servicesCompleted = servicesCompleted;
    }

    public int getBadges() {
        return badges;
    }

    @JsonIgnore
    public void setBadges(int badges) {
        this.badges = badges;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public ProfileRoles getRole() {
        return role;
    }

    public void setRole(ProfileRoles role) {
        this.role = role;
    }

    public int getResponseTimeMinutes() {
        return responseTimeMinutes;
    }

    @JsonIgnore
    public void setResponseTimeMinutes(int responseTimeMinutes) {
        this.responseTimeMinutes = responseTimeMinutes;
    }

    public Bank getBank() {
        return bank;
    }

    public void setBank(Bank bank) {
        this.bank = bank;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
}
