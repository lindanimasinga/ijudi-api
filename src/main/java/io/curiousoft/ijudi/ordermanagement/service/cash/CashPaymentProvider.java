package io.curiousoft.ijudi.ordermanagement.service.cash;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentData;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;
import io.curiousoft.ijudi.ordermanagement.service.PaymentProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CashPaymentProvider extends PaymentProvider {

    public CashPaymentProvider() {
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
    public boolean makePayment(Order order, double basketAmountExclFees) throws Exception {
        return true;
    }

    @Override
    public void makePayments(List ordersList) {
    }
}
