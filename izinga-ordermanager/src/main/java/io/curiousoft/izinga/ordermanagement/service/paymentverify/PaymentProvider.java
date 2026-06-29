package io.curiousoft.izinga.ordermanagement.service.paymentverify;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.PaymentData;
import io.curiousoft.izinga.commons.model.PaymentType;
import io.curiousoft.izinga.commons.model.StoreProfile;

import java.util.List;

public abstract class PaymentProvider<P extends PaymentData> {

    private PaymentType paymentType;

    public PaymentProvider(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    protected abstract boolean paymentReceived(Order order);

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public abstract boolean makePaymentToShop(P paymentData) throws Exception;

    public abstract boolean makePaymentToShop(StoreProfile shop, Order order, double basketAmountExclFees) throws Exception;

    public abstract void makePayments(List<Order> ordersList);

    public abstract void makePaymentToMessenger(Order order, double amount) throws Exception;

    public boolean reversePayment(Order order) {
        return false;
    }
}
