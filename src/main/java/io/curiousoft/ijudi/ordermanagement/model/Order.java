package io.curiousoft.ijudi.ordermanagement.model;

import io.curiousoft.ijudi.ordermanagement.validator.ValidDeliveryInfo;
import io.curiousoft.ijudi.ordermanagement.validator.ValidOrder;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

@ValidOrder
@ValidDeliveryInfo
@Document
public class Order extends BaseModel {

    @NotNull(message = "order stage is not valid")
    private OrderStage stage;

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
    @PositiveOrZero(message = "service fee was modified")
    private double serviceFee;
    private boolean messengerPaid;

    public void setStage(OrderStage stage) {
        this.stage = stage;
    }

    public OrderStage getStage() {
        return stage;
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
        return serviceFee + basket.getItems().stream()
                    .mapToDouble(BasketItem::getPrice).sum() + (shippingData != null? shippingData.getFee() : 0);
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

    public double getBasketAmount() {
        return basket.getItems().stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity()).sum();
    }

    public double getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(double serviceFee) {
        this.serviceFee = serviceFee;
    }

    public boolean getMessengerPaid() {
        return messengerPaid;
    }

    public void setMessengerPaid(boolean messengerPaid) {
        this.messengerPaid = messengerPaid;
    }
}
