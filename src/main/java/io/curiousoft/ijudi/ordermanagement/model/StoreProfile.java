package io.curiousoft.ijudi.ordermanagement.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class StoreProfile extends Profile implements GeoPoint {

    @Indexed(unique = true)
    private String regNumber;
    @Indexed(unique = true)
    @NotBlank(message = "profile short name not valid")
    private String shortName;
    @NotBlank
    String ownerId;
    @Valid
    private Set<Stock> stockList = new HashSet<>();
    @NotEmpty(message = "profile tags not valid")
    private List<@NotEmpty String> tags;
    @NotEmpty(message = "Business hours not valid")
    private List<@NotNull BusinessHours> businessHours;
    private boolean hasVat;
    private boolean featured;
    private Date featuredExpiry;
    @NotNull(message = "storeType is not valid")
    private StoreType storeType;
    private Messager storeMessenger;
    private String storeWebsiteUrl;
    private boolean izingaTakesCommission;
    private boolean scheduledDeliveryAllowed = false;
    private AVAILABILITY availability = AVAILABILITY.SPECIFIC_HOURS;
    private String brandPrimaryColor = "#d69447";
    private String brandSecondaryColor = "#d69447";
    @PositiveOrZero(message = "free delivery min amount must be greater than or equal to 0.01")
    private double freeDeliveryMinAmount;
    private boolean markUpPrice = true;


    public StoreProfile(
                        @NotNull(message = "storeType is not valid") StoreType storeType,
                        @NotBlank(message = "profile name not valid") String name,
                        @NotBlank(message = "profile short name not valid") String shortName,
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
        this.storeType = storeType;
        this.shortName = shortName;
        setBank(bank);
    }

    public void setMarkUp(double markupPerc) {
        if(markUpPrice) {
            stockList.forEach(it -> it.setMarkupPercentage(markupPerc));
        }
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

    public StoreType getStoreType() {
        return storeType;
    }

    public void setStoreType(StoreType storeType) {
        this.storeType = storeType;
    }

    public Messager getStoreMessenger() {
        return storeMessenger;
    }

    public void setStoreMessenger(Messager storeMessenger) {
        this.storeMessenger = storeMessenger;
    }

    public String getOrderUrl() {
        return !StringUtils.isEmpty(storeWebsiteUrl) ? storeWebsiteUrl + "/order/" : null;
    }

    public String getStoreWebsiteUrl() {
        return storeWebsiteUrl;
    }

    public void setStoreWebsiteUrl(String storeWebsiteUrl) {
        this.storeWebsiteUrl = storeWebsiteUrl;
    }

    public boolean getIzingaTakesCommission() {
        return izingaTakesCommission;
    }

    public void setIzingaTakesCommission(boolean izingaTakesCommission) {
        this.izingaTakesCommission = izingaTakesCommission;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName != null ? shortName.toLowerCase() : null;
    }

    public boolean getScheduledDeliveryAllowed() {
        return scheduledDeliveryAllowed;
    }

    public void setScheduledDeliveryAllowed(boolean scheduledDeliveryAllowed) {
        this.scheduledDeliveryAllowed = scheduledDeliveryAllowed;
    }

    public String getBrandPrimaryColor() {
        return brandPrimaryColor;
    }

    public void setBrandPrimaryColor(String brandPrimaryColor) {
        this.brandPrimaryColor = brandPrimaryColor;
    }

    public String getBrandSecondaryColor() {
        return brandSecondaryColor;
    }

    public void setBrandSecondaryColor(String brandSecondaryColor) {
        this.brandSecondaryColor = brandSecondaryColor;
    }

    public double getFreeDeliveryMinAmount() {
        return freeDeliveryMinAmount;
    }

    public void setFreeDeliveryMinAmount(double freeDeliveryMinAmount) {
        this.freeDeliveryMinAmount = freeDeliveryMinAmount;
    }

    public boolean getMarkUpPrice() {
        return markUpPrice;
    }

    public void setMarkUpPrice(boolean markUpPrice) {
        this.markUpPrice = markUpPrice;
    }

    public AVAILABILITY getAvailability() {
        return availability;
    }

    public void setAvailability(AVAILABILITY availability) {
        this.availability = availability;
    }

    public boolean isEligibleForFreeDelivery(Order order) {
        return freeDeliveryMinAmount > 0  && order.getBasketAmount() >= freeDeliveryMinAmount;
    }

    public boolean isStoreOffline() {
        if(availability == AVAILABILITY.OFFLINE) {
            return true;
        }

        if(availability == AVAILABILITY.ONLINE24_7 || businessHours == null || businessHours.isEmpty()) {
            return false;
        }

        if(scheduledDeliveryAllowed) {
            return false;
        }

        Optional<BusinessHours> businessDay = businessHours.stream()
                .filter(day -> LocalDateTime.now().getDayOfWeek() == day.getDay())
                .findFirst();

        if(!businessDay.isPresent()) {
            return true;
        }

        Calendar calender = new Calendar.Builder().setInstant(businessDay.get().getOpen()).build();
        Date open  = Date.from(LocalDateTime.now()
                .withHour(calender.get(Calendar.HOUR_OF_DAY))
                .withMinute(calender.get(Calendar.MINUTE))
                .withSecond(calender.get(Calendar.SECOND))
                .atZone(ZoneId.systemDefault())
                .toInstant());

        calender = new Calendar.Builder().setInstant(businessDay.get().getClose()).build();
        Date close  = Date.from(LocalDateTime.now()
                .withHour(calender.get(Calendar.HOUR_OF_DAY))
                .withMinute(calender.get(Calendar.MINUTE))
                .withSecond(calender.get(Calendar.SECOND))
                .atZone(ZoneId.systemDefault())
                .toInstant());

        Date lowerBoundTime = Date.from(LocalDateTime.now().plusHours(2)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        Date upperBoundTime = Date.from(LocalDateTime.now().plusHours(2).plusMinutes(14) //should not accept orders 15 minutes before store closes
                .atZone(ZoneId.systemDefault())
                .toInstant());
        return lowerBoundTime.before(open)
                || upperBoundTime.after(close);
    }

    public boolean isDeliverNowAllowed() {

        if(businessHours == null || businessHours.isEmpty()) {
            return false;
        }

        Optional<BusinessHours> businessDay = businessHours.stream()
                .filter(day -> LocalDateTime.now().getDayOfWeek() == day.getDay())
                .findFirst();

        if(!businessDay.isPresent()) {
            return false;
        }

        Calendar calender = new Calendar.Builder().setInstant(businessDay.get().getOpen()).build();
        Date open  = Date.from(LocalDateTime.now()
                .withHour(calender.get(Calendar.HOUR_OF_DAY))
                .withMinute(calender.get(Calendar.MINUTE))
                .withSecond(calender.get(Calendar.SECOND))
                .atZone(ZoneId.systemDefault())
                .toInstant());

        calender = new Calendar.Builder().setInstant(businessDay.get().getClose()).build();
        Date close  = Date.from(LocalDateTime.now()
                .withHour(calender.get(Calendar.HOUR_OF_DAY))
                .withMinute(calender.get(Calendar.MINUTE))
                .withSecond(calender.get(Calendar.SECOND))
                .atZone(ZoneId.systemDefault())
                .toInstant());

        Date lowerBoundTime = Date.from(LocalDateTime.now().plusHours(2)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        Date upperBoundTime = Date.from(LocalDateTime.now().plusHours(2).plusMinutes(14) //should not accept orders 15 minutes before store closes
                .atZone(ZoneId.systemDefault())
                .toInstant());
        return lowerBoundTime.after(open)
                || upperBoundTime.before(close);
    }

    public enum AVAILABILITY {
        OFFLINE, SPECIFIC_HOURS, ONLINE24_7
    }
}


