package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.notification.PushNotificationService;
import io.curiousoft.ijudi.ordermanagement.repo.DeviceRepository;
import io.curiousoft.ijudi.ordermanagement.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
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
    private final UserProfileRepo userProfileRepo;
    private double izingaCommissionPerc;

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


    public boolean completePaymentToShop(Order order) throws Exception {
        PaymentProvider paymentProvider = paymentProviders.stream()
                .filter(service -> order.getPaymentType() == service.getPaymentType())
                .findFirst().orElseThrow(() -> new Exception("Your order has no ukheshe type set or the payment provider not configured for " + order.getPaymentType() + " not configured on the server"));

        StoreProfile shop = storeRepository.findById(order.getShopId())
                .orElseThrow(() -> new Exception("shop does not exist"));

        double amountAfterFreeDeliveryCosts = order.getFreeDelivery() ? order.getBasketAmount() - order.getShippingData().getFee() : order.getBasketAmount();
        double amount = shop.getIzingaTakesCommission() ? amountAfterFreeDeliveryCosts - (amountAfterFreeDeliveryCosts * izingaCommissionPerc) : amountAfterFreeDeliveryCosts;
        boolean paid = paymentProvider.makePaymentToShop(shop, order, amount);
        if(paid) {
            order.setShopPaid(paid);
            String content = "Payment of R " + amount + " received";
            PushHeading heading = new PushHeading("Payment of R " + amount + " received",
                    "Order Payment Received", null);
            PushMessage message = new PushMessage(PushMessageType.PAYMENT, heading, content);
            deviceRepo.findByUserId(shop.getOwnerId())
                    .forEach(device -> {
                        try {
                            pushNotificationService.sendNotification(device, message);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        }
        return paid;
    }


    @Scheduled(fixedDelay = 900000) // 15 minutes
    public void processPendingPayments() {
        Date pastDate = Date.from(LocalDateTime.now()
                .minusMinutes(processPaymentIntervalMinutes)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        List<Order> orders = orderRepo
                .findByShopPaidAndStageAndModifiedDateBefore(false,
                        OrderStage.STAGE_6_WITH_CUSTOMER, pastDate);

        logger.info("Processing " + orders.size() + " pending payments");
        orders.forEach(order -> {
                    try {
                        completePaymentToShop(order);
                        order.setStage(OrderStage.STAGE_7_ALL_PAID);
                        order.setShopPaid(true);
                        orderRepo.save(order);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                });

    }

    public void completePaymentToMessenger(Order order) throws Exception {

        PaymentProvider paymentProvider = paymentProviders.stream()
                .filter(service -> order.getPaymentType() == service.getPaymentType())
                .findFirst().orElseThrow(() -> new Exception("Your order has no ukheshe type set or the ukheshe provider for " + order.getPaymentType() + " not configured on the server"));

        paymentProvider.makePaymentToMessenger(order, order.getShippingData().getFee());
        order.setMessengerPaid(true);
        String content = "Payment of R " + order.getShippingData().getFee() + " received";
        PushHeading heading = new PushHeading("Payment of R " + order.getShippingData().getFee() + " received",
                "Order Payment Received", null);
        PushMessage message = new PushMessage(PushMessageType.PAYMENT, heading, content);
        deviceRepo.findByUserId(order.getShippingData().getMessengerId())
                    .forEach(device -> {
                        try {
                            pushNotificationService.sendNotification(device, message);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
    }

    public boolean reversePayment(Order order) throws Exception {
        PaymentProvider paymentProvider = paymentProviders.stream()
                .filter(service -> order.getPaymentType() == service.getPaymentType())
                .findFirst()
                .orElseThrow(() -> new Exception("Your order has no ukheshe type set or the ukheshe provider for " + order.getPaymentType() + " not configured on the server"));
        return paymentProvider.reversePayment(order);
    }
}
