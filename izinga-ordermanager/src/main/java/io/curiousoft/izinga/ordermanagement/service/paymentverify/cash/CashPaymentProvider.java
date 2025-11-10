package io.curiousoft.izinga.ordermanagement.service.paymentverify.cash;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.PaymentData;
import io.curiousoft.izinga.commons.model.PaymentType;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CashPaymentProvider extends PaymentProvider {

    public CashPaymentProvider() {
        super(PaymentType.CASH);
    }

    @Override
    public boolean paymentReceived(Order order) {
        return true;
    }

    @Override
    public boolean makePaymentToShop(PaymentData paymentData) {
        return true;
    }

    @Override
    public boolean makePaymentToShop(StoreProfile store, Order order, double basketAmountExclFees) throws Exception {
        return true;
    }

    @Override
    public void makePaymentToMessenger(Order order, double amount) {
    }

    @Override
    public void makePayments(List ordersList) {
    }
}