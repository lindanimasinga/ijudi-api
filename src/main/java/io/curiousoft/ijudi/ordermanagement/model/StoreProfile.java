package io.curiousoft.ijudi.ordermanagement.model;

import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StoreProfile extends Profile implements GeoPoint {

    @Indexed(unique = true)
    private String regNumber;
    @NotBlank String ownerId;
    @Valid
    private Set<Stock> stockList = new HashSet<>();
    @NotEmpty(message = "profile tags not valid")
    private List<String> tags;
    @NotEmpty(message = "Business hours not valid")
    private List<BusinessHours> businessHours;
    private boolean hasVat;
    private boolean featured;
    private Date featuredExpiry;


    public StoreProfile(@NotBlank(message = "profile name not valid") String name,
                        @NotBlank(message = "profile address not valid") String address,
                        @NotBlank(message = "profile image url not valid") String imageUrl,
                        @NotBlank(message = "profile mobile number not valid") String mobileNumber,
                        @NotEmpty(message = "profile tags not valid") List<String> tags,
                        @NotEmpty(message = "Business hours not valid") List<BusinessHours> businessHours,
                        @NotBlank(message = "shop owner id not valid") String ownerId,
                        @NotNull(message = "Shop bank not valid") Bank bank) {
        super(name, address, imageUrl, mobileNumber, ProfileRoles.STORE);
        this.businessHours = businessHours;
        this.tags = tags;
        this.ownerId = ownerId;
        setBank(bank);
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

    public Set<Stock> getStockList() {
        return stockList;
    }

    public void setStockList(Set<Stock> stockList) {
        this.stockList = stockList;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

}
