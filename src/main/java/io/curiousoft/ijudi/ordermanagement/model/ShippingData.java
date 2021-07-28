package io.curiousoft.ijudi.ordermanagement.model;


import javax.validation.constraints.Future;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

public class ShippingData {

    private String id;
    @NotBlank(message = "shipping address not valid")
    private String fromAddress;
    @NotBlank(message = "Shipping address not valid")
    private String toAddress;
    BuildingType buildingType;
    String unitNumber;
    String buildingName;
    private String additionalInstructions;
    @NotNull(message = "shipping type not valid")
    private ShippingType type;
    private double fee;
    private String messengerId;
    @Future(message = "pickup date must be at least 15 minutes ahead")
    private Date pickUpTime;
    private double distance;

    public ShippingData() {
    }

    public ShippingData(@NotBlank(message = "shipping address not valid") String fromAddress,
                        @NotBlank(message = "shipping destination address id not valid") String toAddress,
                        @NotNull ShippingType type) {
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getAdditionalInstructions() {
        return additionalInstructions;
    }

    public void setAdditionalInstructions(String additionalInstructions) {
        this.additionalInstructions = additionalInstructions;
    }

    public ShippingType getType() {
        return type;
    }

    public void setType(ShippingType type) {
        this.type = type;
    }

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public String getMessengerId() {
        return messengerId;
    }

    public void setMessengerId(String messengerId) {
        this.messengerId = messengerId;
    }

    public Date getPickUpTime() {
        return pickUpTime;
    }

    public void setPickUpTime(Date pickUpTime) {
        this.pickUpTime = pickUpTime;
    }

    public BuildingType getBuildingType() {
        return buildingType;
    }

    public void setBuildingType(BuildingType buildingType) {
        this.buildingType = buildingType;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public void setUnitNumber(String unitNumber) {
        this.unitNumber = unitNumber;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public void setBuildingName(String buildingName) {
        this.buildingName = buildingName;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public enum ShippingType {
        COLLECTION,
        DELIVERY,
        SCHEDULED_DELIVERY
    }
}

