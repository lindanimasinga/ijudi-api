package io.curiousoft.ijudi.ordermanagement.service.cash;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentData;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;
import io.curiousoft.ijudi.ordermanagement.service.PaymentService;
import org.springframework.stereotype.Service;

@Service
public class CashPaymentService extends PaymentService {

    public CashPaymentService() {
        super(PaymentType.CASH);
    }

    @Override
    public boolean paymentReceived(Order order) throws Exception {
        return true;
    }

    @Override
    public boolean makePayment(PaymentData paymentData) {
        return true;
    }

    @Override
    public boolean makePayment(Order order) throws Exception {
        return true;
    }
}
