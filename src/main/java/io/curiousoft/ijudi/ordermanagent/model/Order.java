package io.curiousoft.ijudi.ordermanagent.model;

import io.curiousoft.ijudi.ordermanagent.service.BaseModel;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Document
public class Order extends BaseModel {

    @Min(value = 0, message = "order stage must be between 0 and 3")
    @Max(value = 3, message = "order stage must be between 0 and 3")
    private int stage;
    private Date date;
    @Valid
    @NotNull(message = "order shipping is not valid")
    private ShippingData shippingData;
    @Valid
    @NotNull(message = "order basket is not valid")
    private Basket basket;
    @NotBlank(message = "order customer is not valid")
    private String customerId;
    @NotBlank(message = "order shop is not valid")
    private String shopId;

    public void setDate(Date date) {
        this.date = date;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public int getStage() {
        return stage;
    }

    public Date getDate() {
        return date;
    }

    public ShippingData getShippingData() {
        return shippingData;
    }

    public void setShippingData(ShippingData shippingData) {
        this.shippingData = shippingData;
    }

    public Basket getBasket() {
        return basket;
    }

    public void setBasket(Basket basket) {
        this.basket = basket;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }
}
