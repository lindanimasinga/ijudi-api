package io.curiousoft.izinga.ordermanagement.service.paymentverify.pos;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.PaymentData;
import io.curiousoft.izinga.commons.model.PaymentType;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.repo.OrderRepository;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class POSPaymentProvider extends PaymentProvider {

    private OrderRepository orderRepo;

    public POSPaymentProvider(final OrderRepository orderRepo) {
        super(PaymentType.SPEED_POINT);
        this.orderRepo = orderRepo;
    }

    @Override
    public boolean paymentReceived(Order order) throws Exception {
        var pastOrderCount = orderRepo.findByCustomerId(order.getCustomerId()).map(List::size).orElse(0);
        return pastOrderCount > 2;
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

    @Override
    public boolean reversePayment(Order order) {
        return true;
    }
}
