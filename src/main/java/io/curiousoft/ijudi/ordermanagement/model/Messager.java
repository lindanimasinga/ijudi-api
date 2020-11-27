package io.curiousoft.ijudi.ordermanagement.model;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

public class Messager extends BaseModel {

    @NotEmpty(message = "Messenger name not valid")
    private String name;
    @DecimalMin(value = "0.001", message = "delivery price must be greater than or equal to 0.001")
    private double standardDeliveryPrice;

    public Messager(@NotEmpty(message = "Messenger name not valid") String name, double standardDeliveryPrice) {
        super();
        this.name = name;
        this.standardDeliveryPrice = standardDeliveryPrice;
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
}
