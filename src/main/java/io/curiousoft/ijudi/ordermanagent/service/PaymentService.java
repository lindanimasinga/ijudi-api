package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Order;
import io.curiousoft.ijudi.ordermanagent.model.PaymentType;

import java.net.URISyntaxException;

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
