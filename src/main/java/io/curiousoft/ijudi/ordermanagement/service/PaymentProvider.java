package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentData;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;

import java.util.List;

public abstract class PaymentProvider<P extends PaymentData> {

    private PaymentType paymentType;

    public PaymentProvider(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    protected abstract boolean paymentReceived(Order order) throws Exception;

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public abstract boolean makePayment(P paymentData) throws Exception;

    public abstract boolean makePayment(Order order, double basketAmountExclFees) throws Exception;

    public abstract void makePayments(List<Order> ordersList);
}
