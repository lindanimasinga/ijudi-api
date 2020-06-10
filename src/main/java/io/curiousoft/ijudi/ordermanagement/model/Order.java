package io.curiousoft.ijudi.ordermanagement.model;

import io.curiousoft.ijudi.ordermanagement.service.BaseModel;
import io.curiousoft.ijudi.ordermanagement.validator.ValidDeliveryInfo;
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
    @ValidDeliveryInfo
    @Valid
    private ShippingData shippingData;
    @Valid
    @NotNull(message = "order basket is not valid")
    private Basket basket;
    @NotBlank(message = "order customer is not valid")
    private String customerId;
    @NotBlank(message = "order shop is not valid")
    private String shopId;
    @NotBlank(message = "order description is not valid")
    private String description;
    private PaymentType paymentType;
    @NotNull(message = "order type is not valid")
    private OrderType orderType;
    private boolean hasVat;
    private boolean shopPaid;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public double getTotalAmount() {
        return basket.getItems().stream()
                    .mapToDouble(BasketItem::getPrice).sum() + shippingData.getFee();
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public boolean getHasVat() {
        return hasVat;
    }

    public void setHasVat(boolean hasVat) {
        this.hasVat = hasVat;
    }

    public boolean getShopPaid() {
        return shopPaid;
    }

    public void setShopPaid(boolean shopPaid) {
        this.shopPaid = shopPaid;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Order && getId().equals(((Order) obj).getId());
    }
}
