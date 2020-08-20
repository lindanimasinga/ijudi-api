package io.curiousoft.ijudi.ordermanagement.model;

import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Stock {

    @NotBlank(message = "stock name must not be blank")
    private String name;
    @Min(value = 1, message = "stock quantity not valid")
    private int quantity;
    @DecimalMin(value = "0.01", message = "stock price must be greater than or equal to 0.01")
    private double price;
    @Min(value = 0, message = "discount % must be >= 0")
    private double discountPerc;
    private List<String> images;
    @NotNull(message = "mandatorySelection not valid")
    private List<SelectionOption> mandatorySelection;
    private List<SelectionOption> optionalSelection;

    public Stock(@NotBlank(message = "stock name must not be blank") String name,
                 @Min(value = 1) int quantity,
                 @DecimalMin(value = "0.01") double price,
                 @Min(value = 0) double discountPerc,
                 @NotNull List<SelectionOption> mandatorySelection) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.discountPerc = discountPerc;
        this.mandatorySelection = mandatorySelection;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getDiscountPerc() {
        return discountPerc;
    }

    public void setDiscountPerc(double discountPerc) {
        this.discountPerc = discountPerc;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<SelectionOption> getMandatorySelection() {
        return mandatorySelection;
    }

    public void setMandatorySelection(List<SelectionOption> mandatorySelection) {
        this.mandatorySelection = mandatorySelection;
    }

    public List<SelectionOption> getOptionalSelection() {
        return optionalSelection;
    }

    public void setOptionalSelection(List<SelectionOption> optionalSelection) {
        this.optionalSelection = optionalSelection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stock stock = (Stock) o;
        return Objects.equals(name, stock.name);
    }
}
