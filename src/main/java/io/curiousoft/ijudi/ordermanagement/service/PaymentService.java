package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentData;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;
import io.curiousoft.ijudi.ordermanagement.service.ukheshe.UkheshePaymentData;

import java.net.URISyntaxException;

public abstract class PaymentService<P extends PaymentData> {

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

    public abstract boolean makePayment(P paymentData) throws Exception;

    public abstract boolean makePayment(Order order) throws Exception;
}
