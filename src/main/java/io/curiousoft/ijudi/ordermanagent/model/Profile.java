package io.curiousoft.ijudi.ordermanagent.model;


import io.curiousoft.ijudi.ordermanagent.service.BaseModel;
import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.NotBlank;

public class Profile extends BaseModel {

    @NotBlank(message = "profile name not valid")
    private String name;
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
    private String mobileNumber;
    @NotBlank(message = "role not valid")
    private String role;
    private int responseTimeMinutes;
    private String verificationCode;
    private Bank bank;


    public Profile(@NotBlank(message = "profile name not valid") String name,
                   @NotBlank(message = "profile address not valid") String address,
                   @NotBlank(message = "profile image url not valid") String imageUrl,
                   @NotBlank(message = "profile mobile number not valid") String mobileNumber,
                   @NotBlank(message = "role not valid") String role) {
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

    public void setServicesCompleted(int servicesCompleted) {
        this.servicesCompleted = servicesCompleted;
    }

    public int getBadges() {
        return badges;
    }

    public void setBadges(int badges) {
        this.badges = badges;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getResponseTimeMinutes() {
        return responseTimeMinutes;
    }

    public void setResponseTimeMinutes(int responseTimeMinutes) {
        this.responseTimeMinutes = responseTimeMinutes;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public Bank getBank() {
        return bank;
    }

    public void setBank(Bank bank) {
        this.bank = bank;
    }
}
