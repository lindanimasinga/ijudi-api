package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.notification.PushNotificationService;
import io.curiousoft.ijudi.ordermanagement.repo.DeviceRepository;
import io.curiousoft.ijudi.ordermanagement.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
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

    public PaymentService(PushNotificationService pushNotificationService,
                          List<PaymentProvider> paymentProviders, OrderRepository orderRepo,
                          DeviceRepository deviceRepo,
                          StoreRepository storeRepository,
                          @Value("${payment.process.pending.minutes}") long processPaymentIntervalMinutes) {
        this.paymentProviders = paymentProviders;
        this.orderRepo = orderRepo;
        this.processPaymentIntervalMinutes = processPaymentIntervalMinutes;
        this.pushNotificationService = pushNotificationService;
        this.deviceRepo = deviceRepo;
        this.storeRepository = storeRepository;
    }

    public boolean paymentReceived(Order order) throws Exception {
        PaymentProvider paymentProvider = paymentProviders.stream()
                .filter(service -> order.getPaymentType() == service.getPaymentType())
                .findFirst().orElseThrow(() -> new Exception("Your order has no ukheshe type set or the ukheshe provider for " + order.getPaymentType() + " not configured on the server"));
        return paymentProvider.paymentReceived(order);
    }


    public boolean completePaymentToShop(Order order) throws Exception {
        PaymentProvider paymentProvider = paymentProviders.stream()
                .filter(service -> order.getPaymentType() == service.getPaymentType())
                .findFirst().orElseThrow(() -> new Exception("Your order has no ukheshe type set or the ukheshe provider for " + order.getPaymentType() + " not configured on the server"));

        paymentProvider.makePayment(order);
        String content = "Payment of R " + order.getTotalAmount() + " received";
        PushHeading heading = new PushHeading("Payment of R " + order.getTotalAmount() + " received",
                "Order Payment Received", null);
        PushMessage message = new PushMessage(PushMessageType.PAYMENT, heading, content);
        StoreProfile shop = storeRepository.findById(order.getShopId()).orElse(null);
        if(shop != null) {
            deviceRepo.findByUserId(shop.getOwnerId())
                    .forEach(device -> {
                        try {
                            pushNotificationService.sendNotification(device, message);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        }
        return true;
    }


    @Scheduled(fixedDelay = 900000) // 15 minutes
    public void processPendingPayments() {
        Date pastDate = Date.from(LocalDateTime.now()
                .minusSeconds(processPaymentIntervalMinutes)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        List<Order> orders = orderRepo
                .findByShopPaidAndStageAndDateBefore(false,
                        OrderStage.STAGE_6_WITH_CUSTOMER, pastDate);

        logger.info("Processing " + orders.size() + " pending payments");
        orders.forEach(order -> {
                    try {
                        completePaymentToShop(order);
                        order.setStage(OrderStage.STAGE_7_PAID_SHOP);
                        order.setShopPaid(true);
                        orderRepo.save(order);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                });

    }
}
