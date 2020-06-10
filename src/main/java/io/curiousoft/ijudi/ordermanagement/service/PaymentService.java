package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;

public abstract class PaymentService {

    private PaymentType paymentType;

    public PaymentService(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    protected abstract boolean paymentReceived(Order order) throws Exception;

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }
}
