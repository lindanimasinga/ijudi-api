package io.curiousoft.izinga.ordermanagement.leads;

import io.curiousoft.izinga.commons.model.BaseModel;
import io.curiousoft.izinga.commons.model.StoreType;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "leads")
public class Lead extends BaseModel {

    @Indexed(unique = true, sparse = true)
    private String phone;

    private List<LeadItem> items;
    private String fromAddress;
    private String toAddress;
    private double deliveryPrice;
    private double totalPrice;
    private StoreType storeType;
    private String storeId;
    private LeadStatus status;
    private boolean anonymous;
    private boolean consentGiven;
    private Instant consentTimestamp;
    private String category;
    private double distanceKm;
    private double estimatedDeliveryFee;
    private double ratePerKm;
    private double standardFee;
    private double standardKm;

    public Lead() {}

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<LeadItem> getItems() { return items; }
    public void setItems(List<LeadItem> items) { this.items = items; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public double getDeliveryPrice() { return deliveryPrice; }
    public void setDeliveryPrice(double deliveryPrice) { this.deliveryPrice = deliveryPrice; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public StoreType getStoreType() { return storeType; }
    public void setStoreType(StoreType storeType) { this.storeType = storeType; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public LeadStatus getStatus() { return status; }
    public void setStatus(LeadStatus status) { this.status = status; }

    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }

    public boolean isConsentGiven() { return consentGiven; }
    public void setConsentGiven(boolean consentGiven) { this.consentGiven = consentGiven; }

    public Instant getConsentTimestamp() { return consentTimestamp; }
    public void setConsentTimestamp(Instant consentTimestamp) { this.consentTimestamp = consentTimestamp; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }

    public double getEstimatedDeliveryFee() { return estimatedDeliveryFee; }
    public void setEstimatedDeliveryFee(double estimatedDeliveryFee) { this.estimatedDeliveryFee = estimatedDeliveryFee; }

    public double getRatePerKm() { return ratePerKm; }
    public void setRatePerKm(double ratePerKm) { this.ratePerKm = ratePerKm; }

    public double getStandardFee() { return standardFee; }
    public void setStandardFee(double standardFee) { this.standardFee = standardFee; }

    public double getStandardKm() { return standardKm; }
    public void setStandardKm(double standardKm) { this.standardKm = standardKm; }
}
