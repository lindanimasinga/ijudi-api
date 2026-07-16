package io.curiousoft.izinga.ordermanagement.leads;

import io.curiousoft.izinga.commons.model.StoreType;

import java.time.Instant;
import java.util.List;

public class LeadRequest {
    private String phone;
    private List<LeadItem> items;
    private String fromAddress;
    private String toAddress;
    private double estimatedPrice;
    private StoreType storeType;
    private String storeId;
    private boolean consentGiven;
    private Instant consentTimestamp;
    private String category;
    private Double distanceKm;
    private Double standardFee;
    private Double standardKm;
    private Double ratePerKm;
    private Double estimatedDeliveryFee;

    public LeadRequest() {}

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<LeadItem> getItems() { return items; }
    public void setItems(List<LeadItem> items) { this.items = items; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public double getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(double estimatedPrice) { this.estimatedPrice = estimatedPrice; }

    public StoreType getStoreType() { return storeType; }
    public void setStoreType(StoreType storeType) { this.storeType = storeType; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public boolean isConsentGiven() { return consentGiven; }
    public void setConsentGiven(boolean consentGiven) { this.consentGiven = consentGiven; }

    public Instant getConsentTimestamp() { return consentTimestamp; }
    public void setConsentTimestamp(Instant consentTimestamp) { this.consentTimestamp = consentTimestamp; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Double getStandardFee() { return standardFee; }
    public void setStandardFee(Double standardFee) { this.standardFee = standardFee; }

    public Double getStandardKm() { return standardKm; }
    public void setStandardKm(Double standardKm) { this.standardKm = standardKm; }

    public Double getRatePerKm() { return ratePerKm; }
    public void setRatePerKm(Double ratePerKm) { this.ratePerKm = ratePerKm; }

    public Double getEstimatedDeliveryFee() { return estimatedDeliveryFee; }
    public void setEstimatedDeliveryFee(Double estimatedDeliveryFee) { this.estimatedDeliveryFee = estimatedDeliveryFee; }
}
