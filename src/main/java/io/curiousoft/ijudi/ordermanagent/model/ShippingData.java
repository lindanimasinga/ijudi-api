package io.curiousoft.ijudi.ordermanagent.model;


import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

public class ShippingData {

    private String id;
    @NotBlank(message = "shipping address id not valid")
    private String fromAddress;
    private String toAddress;
    private String additionalInstructions;
    @NotNull
    private ShippingType type;
    private double fee;
    private Messager messenger;
    private Date pickUpTime;

    public ShippingData() {
    }

    public ShippingData(@NotBlank(message = "shipping address id not valid") String fromAddress,
                        @NotBlank(message = "shipping destination address id not valid") String toAddress,
                        @NotNull ShippingType type,
                        double fee) {
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.type = type;
        this.fee = fee;
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

    public Messager getMessenger() {
        return messenger;
    }

    public void setMessenger(Messager messenger) {
        this.messenger = messenger;
    }

    public Date getPickUpTime() {
        return pickUpTime;
    }

    public void setPickUpTime(Date pickUpTime) {
        this.pickUpTime = pickUpTime;
    }

    public static enum ShippingType {
        COLLECTION,
        DELIVERY
    }
}

