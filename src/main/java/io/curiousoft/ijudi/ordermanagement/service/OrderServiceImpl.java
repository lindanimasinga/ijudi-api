package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.notification.FirebaseNotificationService;
import io.curiousoft.ijudi.ordermanagement.notification.PushNotificationService;
import io.curiousoft.ijudi.ordermanagement.repo.DeviceRepository;
import io.curiousoft.ijudi.ordermanagement.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static io.curiousoft.ijudi.ordermanagement.model.OrderType.INSTORE;
import static io.curiousoft.ijudi.ordermanagement.model.OrderType.ONLINE;

@Service
public class OrderServiceImpl implements OrderService {

    private static Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    public static final String DATE_FORMAT = "SSSmmHHyyddMMss";
    private final LinkedList<OrderStage> onlineCollectionStages;
    private final LinkedList<OrderStage> onlineDeliveryStages;
    private final OrderRepository orderRepo;
    private final StoreRepository storeRepository;
    private final UserProfileRepo userProfileRepo;
    private final Validator validator;
    private final PaymentService paymentService;
    private final DeviceRepository deviceRepo;
    private final PushNotificationService pushNotificationService;
    private final double deliveryFee;
    private final double serviceFee;
    private final long cleanUpMinutes;

    @Autowired
    public OrderServiceImpl(@Value("${service.delivery.fee}") double deliveryFee,
                            @Value("${service.fee}") double serviceFee,
                            @Value("${order.cleanup.unpaid.minutes}") long cleanUpMinutes,
                            OrderRepository orderRepository,
                            StoreRepository storeRepository,
                            UserProfileRepo userProfileRepo, PaymentService paymentService,
                            DeviceRepository deviceRepo,
                            PushNotificationService pushNotificationService) {
        this.deliveryFee = deliveryFee;
        this.serviceFee = serviceFee;
        this.cleanUpMinutes = cleanUpMinutes;
        this.orderRepo = orderRepository;
        this.storeRepository = storeRepository;
        this.userProfileRepo = userProfileRepo;
        this.paymentService = paymentService;
        this.pushNotificationService = pushNotificationService;
        this.deviceRepo = deviceRepo;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        onlineDeliveryStages = new LinkedList<>();
        onlineDeliveryStages.add(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        onlineDeliveryStages.add(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        onlineDeliveryStages.add(OrderStage.STAGE_2_STORE_PROCESSING);
        onlineDeliveryStages.add(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        onlineDeliveryStages.add(OrderStage.STAGE_4_ON_THE_ROAD);
        onlineDeliveryStages.add(OrderStage.STAGE_5_ARRIVED);
        onlineDeliveryStages.add(OrderStage.STAGE_6_WITH_CUSTOMER);
        onlineDeliveryStages.add(OrderStage.STAGE_7_PAID_SHOP);

        onlineCollectionStages = new LinkedList<>();
        onlineCollectionStages.add(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        onlineCollectionStages.add(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        onlineCollectionStages.add(OrderStage.STAGE_2_STORE_PROCESSING);
        onlineCollectionStages.add(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        onlineCollectionStages.add(OrderStage.STAGE_6_WITH_CUSTOMER);
        onlineCollectionStages.add(OrderStage.STAGE_7_PAID_SHOP);
    }

    @Override
    public Order startOrder(Order order) throws Exception {

        validate(order);
        if (!userProfileRepo.existsById(order.getCustomerId())) {
            throw new Exception("user with id " + order.getCustomerId() + " does not exist");
        }

        Optional<StoreProfile> storeOptional = storeRepository.findById(order.getShopId());
        if (!storeOptional.isPresent()) {
            throw new Exception("shop with id " + order.getShopId() + " does not exist");
        }
        order.setHasVat(storeOptional.get().getHasVat());
        Date orderDate = new Date();
        String orderId = new SimpleDateFormat(DATE_FORMAT).format(orderDate);
        order.setId(orderId);
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setDate(orderDate);
        order.setServiceFee(serviceFee);
        if(order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY) {
            order.getShippingData().setFee(deliveryFee);
        }
        return orderRepo.save(order);
    }

    @Override
    public Order finishOder(Order order) throws Exception {
        validate(order);

        Order persistedOrder = orderRepo.findById(order.getId())
                .orElseThrow(() -> new Exception("Order with id " + order.getId() + " not found."));

        persistedOrder.setDescription(order.getDescription());
        persistedOrder.setPaymentType(order.getPaymentType());
        persistedOrder.setDate(new Date());
        order.setServiceFee(serviceFee);
        if(order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY) {
            order.getShippingData().setFee(deliveryFee);
        }

        if (!paymentService.paymentReceived(persistedOrder)) {
            throw new Exception("Payment not cleared yet. please verify again.");
        }

        if(persistedOrder.getOrderType() == INSTORE) {
            if(persistedOrder.getShopPaid()) {
                return order;
            }
            paymentService.completePaymentToShop(persistedOrder);
            persistedOrder.setStage(OrderStage.STAGE_7_PAID_SHOP);
            persistedOrder.setShopPaid(true);
        } else {
            persistedOrder.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        }

        //decrease stock available
        Optional<StoreProfile> optional = storeRepository.findById(order.getShopId());
        if (optional.isPresent()) {
            StoreProfile store = optional.get();
            //notify the shop
            List<Device> shopDevices = deviceRepo.findByUserId(store.getOwnerId());
            pushNotificationService.notifyStoreOrderPlaced(shopDevices, order);
            Set<Stock> stock = store.getStockList();
            order.getBasket()
                    .getItems()
                    .stream()
                    .forEach(item -> {
                        stock.stream()
                                .filter(sto -> sto.getName().equals(item.getName()))
                                .forEach(stockItem -> stockItem.setQuantity(stockItem.getQuantity() - item.getQuantity()));
                    });
            store.setServicesCompleted(store.getServicesCompleted() + 1);
            storeRepository.save(store);

            if(order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY) {
                List<Device> messengerDevices = deviceRepo.findByUserId(order.getShippingData().getMessenger().getId());
                pushNotificationService.notifyMessengerOrderPlaced(messengerDevices, order, store);
            }
        }

        return orderRepo.save(persistedOrder);
    }

    @Override
    public Order findOrder(String orderId) {
        return orderRepo.findById(orderId).orElse(null);
    }

    @Override
    public Order progressNextStage(String orderId) throws Exception {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new Exception("Order with id " + orderId + " not found."));
        int index = onlineDeliveryStages.indexOf(order.getStage());

        if(order.getOrderType() == INSTORE || index >= onlineDeliveryStages.size() - 2) { // last stage is updated by the payment process
            return order;
        }

        switch (order.getShippingData().getType()) {
            case DELIVERY:
                OrderStage stage = onlineDeliveryStages.get(index + 1);
                order.setStage(stage);
                break;
            case COLLECTION:
                OrderStage collectionStage = onlineCollectionStages.get(index + 1);
                order.setStage(collectionStage);
                break;
        }

        final String order_status_updated = "Order Status Updated";
        switch (order.getStage()) {
            case STAGE_2_STORE_PROCESSING:
                //notify only customer
                deviceRepo.findByUserId(order.getCustomerId()).forEach(device -> {
                    PushHeading title = new PushHeading("The store has started processing your order " + order.getId(),
                            order_status_updated, null);
                    PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);
                    try {
                        pushNotificationService.sendNotification(device, message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                break;
            case STAGE_3_READY_FOR_COLLECTION:
                StoreProfile shop = storeRepository.findById(order.getShopId()).get();
                deviceRepo.findByUserId(order.getShippingData().getMessenger().getId()).forEach(device -> {
                    PushHeading title = new PushHeading("Food is ready for Collection at " + shop.getName(),
                            order_status_updated, null);
                    PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);
                    try {
                        pushNotificationService.sendNotification(device, message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                break;
            case STAGE_4_ON_THE_ROAD:
                deviceRepo.findByUserId(order.getCustomerId()).forEach(device -> {
                    PushHeading title = new PushHeading("The driver is on the way",
                            order_status_updated, null);
                    PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);
                    try {
                        pushNotificationService.sendNotification(device, message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                break;
            case STAGE_5_ARRIVED:
                deviceRepo.findByUserId(order.getCustomerId()).forEach(device -> {
                    PushHeading title = new PushHeading("The driver has arrived",
                            order_status_updated, null);
                    PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);
                    try {
                        pushNotificationService.sendNotification(device, message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                break;

        }

        orderRepo.save(order);
        return order;
    }

    @Override
    public List<Order> findOrderByUserId(String userId) {
        return orderRepo.findByCustomerId(userId).orElse(new ArrayList<>());
    }

    @Override
    public List<Order> findOrderByPhone(String phone) throws Exception {
        UserProfile user = userProfileRepo.findByMobileNumber(phone)
                .orElseThrow(() -> new Exception("User not found"));
        return orderRepo.findByCustomerId(user.getId()).orElse(new ArrayList<>());
    }

    @Override
    public List<Order> findOrderByStoreId(String shopId) throws Exception {
        StoreProfile store = storeRepository.findById(shopId)
                .orElseThrow(() -> new Exception("Store not found"));
        return orderRepo.findByShopIdAndStageNot(store.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }

    @Scheduled(fixedDelay = 900000) // 15 minutes
    @Override
    public void cleanUnpaidOrders() {
        Date pastDate = Date.from(LocalDateTime.now()
                .minusMinutes(cleanUpMinutes)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        LOGGER.info("Cleaning up all orders before   s" + pastDate);
        orderRepo.deleteByShopPaidAndStageAndDateBefore(false, OrderStage.STAGE_0_CUSTOMER_NOT_PAID, pastDate);
    }

    private void validate(Order order) throws Exception {
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        if (violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }
}
