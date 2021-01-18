package io.curiousoft.ijudi.ordermanagement.model;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

public class Messager extends BaseModel {

    @NotEmpty(message = "Messenger name not valid")
    private String name;
    @DecimalMin(value = "0.01", message = "delivery price must be greater than or equal to 0.01")
    private double standardDeliveryPrice;
    @DecimalMin(value = "0.1", message = "Distance must be greater than or equal to 0.1km")
    private double standardDeliveryKm;
    @DecimalMin(value = "0.01", message = "ratePerKm must be greater than or equal to 0.01")
    private double ratePerKm;

    public Messager(@NotEmpty(message = "Messenger name not valid") String name,
                    @DecimalMin(value = "0.001", message = "delivery price must be greater than or equal to 0.001") double standardDeliveryPrice,
                    @DecimalMin(value = "0.1", message = "Distance must be greater than or equal to 0.1km") double standardDeliveryKm,
                    @DecimalMin(value = "0.01", message = "ratePerKm must be greater than or equal to 0.01") double ratePerKm
                    ) {
        super();
        this.name = name;
        this.standardDeliveryPrice = standardDeliveryPrice;
        this.standardDeliveryKm = standardDeliveryKm;
        this.ratePerKm = ratePerKm;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getStandardDeliveryPrice() {
        return standardDeliveryPrice;
    }

    public void setStandardDeliveryPrice(double standardDeliveryPrice) {
        this.standardDeliveryPrice = standardDeliveryPrice;
    }

    public double getStandardDeliveryKm() {
        return standardDeliveryKm;
    }

    public void setStandardDeliveryKm(double standardDeliveryKm) {
        this.standardDeliveryKm = standardDeliveryKm;
    }

    public double getRatePerKm() {
        return ratePerKm;
    }

    public void setRatePerKm(double ratePerKm) {
        this.ratePerKm = ratePerKm;
    }
}
