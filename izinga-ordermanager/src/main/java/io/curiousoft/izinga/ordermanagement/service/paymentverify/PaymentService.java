package io.curiousoft.izinga.ordermanagement.service.paymentverify;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.PaymentType;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentService {

    Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final List<PaymentProvider> paymentProviders;
    private final OrderRepository orderRepo;
    private final long processPaymentIntervalMinutes;
    private final PushNotificationService pushNotificationService;
    private final DeviceRepository deviceRepo;
    private final StoreRepository storeRepository;
    private final UserProfileRepo userProfileRepo;
    private final double izingaCommissionPerc;

    public PaymentService(PushNotificationService pushNotificationService,
                          List<PaymentProvider> paymentProviders, OrderRepository orderRepo,
                          DeviceRepository deviceRepo,
                          StoreRepository storeRepository,
                          UserProfileRepo userProfileRepo,
                          @Value("${payment.process.pending.minutes}") long processPaymentIntervalMinutes,
                          @Value("${service.commission.perc}") double izingaCommissionPerc) {
        this.paymentProviders = paymentProviders;
        this.orderRepo = orderRepo;
        this.processPaymentIntervalMinutes = processPaymentIntervalMinutes;
        this.pushNotificationService = pushNotificationService;
        this.deviceRepo = deviceRepo;
        this.storeRepository = storeRepository;
        this.userProfileRepo = userProfileRepo;
        this.izingaCommissionPerc = izingaCommissionPerc;
    }

    public boolean paymentReceived(Order order) throws Exception {
        PaymentProvider paymentProvider = paymentProviders.stream()
                .filter(service -> order.getPaymentType() == service.getPaymentType())
                .findFirst()
                .orElseThrow(() -> new Exception("Your order has no ukheshe type set or the ukheshe provider for " + order.getPaymentType() + " not configured on the server"));
        return paymentProvider.paymentReceived(order);
    }

    public boolean reversePayment(Order order) throws Exception {
        PaymentProvider paymentProvider = paymentProviders.stream()
                .filter(service -> order.getPaymentType() == service.getPaymentType())
                .findFirst()
                .orElseThrow(() -> new Exception("Your order has no ukheshe type set or the ukheshe provider for " + order.getPaymentType() + " not configured on the server"));
        return paymentProvider.reversePayment(order);
    }

    public List<PaymentType> getAllowedPaymentTypes(String customerId, StoreType storeType) {
            List<PaymentType> paymentsTypes = new ArrayList<>();
            paymentsTypes.add(PaymentType.YOCO);
            var pastOrders = orderRepo.findByCustomerId(customerId);
            if (pastOrders == null || storeType == StoreType.TIPS) {
                return paymentsTypes;
            }

            var pastOrderCount = orderRepo.findByCustomerId(customerId).map(List::size).orElse(0);
            if (pastOrderCount > 2) {
                paymentsTypes.add(PaymentType.SPEED_POINT);
                paymentsTypes.add(PaymentType.CASH);
            }
            return paymentsTypes;
        }
}
