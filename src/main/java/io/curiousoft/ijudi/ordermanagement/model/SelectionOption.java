package io.curiousoft.ijudi.ordermanagement.model;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

public class SelectionOption {

    @NotEmpty
    private String name;
    @NotEmpty
    private String value;
    @Min(value = 0)
    private double price;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
