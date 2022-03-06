package io.curiousoft.ijudi.ordermanagement.model;

import io.curiousoft.ijudi.ordermanagement.utils.IjudiUtils;
import org.springframework.data.annotation.Transient;

import javax.validation.constraints.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Stock {

    String id = UUID.randomUUID().toString();
    @NotBlank(message = "stock name must not be blank")
    private String name;
    private String description;
    private String detailedDescription;
    private List<String> tags;
    private String group;
    private int position = 10000;
    @Min(value = 0, message = "stock quantity not valid")
    private int quantity;
    @DecimalMin(value = "0.000", message = "stock price must be greater than or equal to 0.001")
    private double storePrice;
    @Min(value = 0, message = "discount % must be >= 0")
    @Max(value = 1, message = "discount % must be <= 1")
    private double discountPerc;
    private List<String> images;
    @NotNull(message = "mandatorySelection not valid")
    private List<SelectionOption> mandatorySelection;
    private List<SelectionOption> optionalSelection;
    @Transient
    private double markupPercentage;

    public Stock() {
    }

    public Stock(@NotBlank(message = "stock name must not be blank") String name,
                 @Min(value = 0) int quantity,
                 @DecimalMin(value = "0.000", message = "stock price must be greater than or equal to 0.001") double storePrice,
                 @Min(value = 0) double discountPerc,
                 @NotNull List<SelectionOption> mandatorySelection) {
        this.name = name;
        this.quantity = quantity;
        this.storePrice = storePrice;
        this.discountPerc = discountPerc;
        this.mandatorySelection = mandatorySelection;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
        return markupPercentage > 0 ? IjudiUtils.calculateMarkupPrice(storePrice, markupPercentage) : storePrice;
    }

    public double getStorePrice() {
        return storePrice;
    }

    public void setStorePrice(double storePrice) {
        this.storePrice = storePrice;
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

    public String getDetailedDescription() {
        return detailedDescription;
    }

    public void setDetailedDescription(String detailedDescription) {
        this.detailedDescription = detailedDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stock stock = (Stock) o;
        return Objects.equals(name, stock.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public void setMarkupPercentage(double markupPercentage) {
        this.markupPercentage = markupPercentage;
    }
}
