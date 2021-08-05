package io.curiousoft.ijudi.ordermanagement.model;

import io.curiousoft.ijudi.ordermanagement.validator.ValidDeliveryInfo;
import io.curiousoft.ijudi.ordermanagement.validator.ValidOrderType;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@ValidOrderType
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
    private boolean smsSentToShop;
    private boolean smsSentToAdmin;
    private boolean freeDelivery;
    private double minimumDepositAllowedPerc;

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
        return BigDecimal.valueOf(serviceFee + basket.getItems().stream()
                .mapToDouble(BasketItem::getTotalPrice).sum() + (!freeDelivery && shippingData != null ? shippingData.getFee() : 0))
                .setScale(2, RoundingMode.HALF_EVEN)
                .doubleValue();
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
        return BigDecimal.valueOf(basket.getItems().stream()
                .mapToDouble(BasketItem::getTotalPrice).sum())
                .setScale(2, RoundingMode.HALF_EVEN)
                .doubleValue();
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

    public static String generateId() {
        return (LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)) + "";
    }

    public void setSmsSentToShop(boolean smsSentToShop) {
        this.smsSentToShop = smsSentToShop;
    }

    public boolean getSmsSentToShop() {
        return smsSentToShop;
    }

    public void setSmsSentToAdmin(boolean smsSentToAdmin) {
        this.smsSentToAdmin = smsSentToAdmin;
    }

    public boolean getSmsSentToAdmin() {
        return smsSentToAdmin;
    }

    public boolean getFreeDelivery() {
        return freeDelivery;
    }

    public void setFreeDelivery(boolean freeDelivery) {
        this.freeDelivery = freeDelivery;
    }

    public double getDepositAmount() {
        return BigDecimal.valueOf(serviceFee
                + basket.getItems().stream().mapToDouble(BasketItem::getTotalPrice).sum() * minimumDepositAllowedPerc
                + (!freeDelivery && shippingData != null ? shippingData.getFee() : 0))
                .setScale(2, RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    public void setMinimumDepositAllowedPerc(double minimumDepositAllowedPerc) {
        this.minimumDepositAllowedPerc = minimumDepositAllowedPerc;
    }

    public double getMinimumDepositAllowedPerc() {
        return minimumDepositAllowedPerc;
    }
}
