package io.curiousoft.ijudi.ordermanagent.model;

import io.curiousoft.ijudi.ordermanagent.service.BaseModel;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Document
public class Order extends BaseModel {

    @Min(value = 0, message = "order stage must be between 0 and 3") @Max(value = 3, message = "order stage must be between 0 and 3")
    private int stage;
    private Date date;
    @Valid
    @NotNull(message = "order shipping is not valid")
    private ShippingData shippingData;
    @Valid
    @NotNull(message = "order basket is not valid")
    private Basket basket;
    @Valid
    @NotNull(message = "order customer is not valid")
    private Customer customer;

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

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
}
