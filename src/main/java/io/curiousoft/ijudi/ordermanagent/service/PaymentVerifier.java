package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentVerifier {

    private final List<PaymentService> paymentServices;

    public PaymentVerifier(List<PaymentService> paymentServices) {
        this.paymentServices = paymentServices;
    }

    public boolean paymentReceived(Order order) throws Exception {
        PaymentService paymentService = paymentServices.stream()
                .filter(service -> order.getPaymentType() == service.getPaymentType())
                .findFirst().orElseThrow(() -> new Exception("Your order has no ukheshe type set or the ukheshe provider for " + order.getPaymentType() + " not configured on the server"));
        return paymentService.paymentReceived(order);
    }
}
