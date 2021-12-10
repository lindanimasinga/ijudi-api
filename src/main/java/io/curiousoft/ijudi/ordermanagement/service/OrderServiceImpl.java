package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static io.curiousoft.ijudi.ordermanagement.model.OrderStage.*;
import static io.curiousoft.ijudi.ordermanagement.model.OrderType.INSTORE;
import static io.curiousoft.ijudi.ordermanagement.model.OrderType.ONLINE;
import static io.curiousoft.ijudi.ordermanagement.utils.IjudiUtils.calculateDeliveryFee;
import static io.curiousoft.ijudi.ordermanagement.utils.IjudiUtils.calculateDrivingDirectionKM;
import static java.lang.String.format;

@Service
public class OrderServiceImpl implements OrderService {

    private static Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final LinkedList<OrderStage> onlineCollectionStages;
    private final LinkedList<OrderStage> onlineDeliveryStages;
    private final OrderRepository orderRepo;
    private final StoreRepository storeRepository;
    private final UserProfileRepo userProfileRepo;
    private final Validator validator;
    private final PaymentService paymentService;
    private final DeviceRepository deviceRepo;
    private final PushNotificationService pushNotificationService;
    private final AdminOnlyNotificationService smsNotificationService;
    private final double starndardDeliveryFee;
    private final double serviceFeePerc;
    private final long cleanUpMinutes;
    private final List<String> adminCellNumbers;
    private final String googleMapsApiKey;
    private final double starndardDeliveryKm;
    private final double ratePerKm;

    @Autowired
    public OrderServiceImpl(@Value("${service.delivery.standardFee}") double starndardDeliveryFee,
                            @Value("${service.delivery.standardKm}") double starndardDeliveryKm,
                            @Value("${service.delivery.ratePerKm}") double ratePerKm,
                            @Value("${service.fee.perc}") double serviceFeePerc,
                            @Value("${order.cleanup.unpaid.minutes}") long cleanUpMinutes,
                            @Value("${admin.cellNumber}") List<String> adminCellNumbers,
                            @Value("${google.maps.api.key}") String googleMapsApiKey,
                            OrderRepository orderRepository,
                            StoreRepository storeRepository,
                            UserProfileRepo userProfileRepo, PaymentService paymentService,
                            DeviceRepository deviceRepo,
                            PushNotificationService pushNotificationService,
                            AdminOnlyNotificationService smsNotifcationService) {
        this.starndardDeliveryFee = starndardDeliveryFee;
        this.starndardDeliveryKm = starndardDeliveryKm;
        this.ratePerKm = ratePerKm;
        this.serviceFeePerc = serviceFeePerc;
        this.cleanUpMinutes = cleanUpMinutes;
        this.adminCellNumbers = adminCellNumbers;
        this.orderRepo = orderRepository;
        this.storeRepository = storeRepository;
        this.userProfileRepo = userProfileRepo;
        this.paymentService = paymentService;
        this.pushNotificationService = pushNotificationService;
        this.smsNotificationService = smsNotifcationService;
        this.deviceRepo = deviceRepo;
        this.googleMapsApiKey = googleMapsApiKey;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        onlineDeliveryStages = new LinkedList<>();
        onlineDeliveryStages.add(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        onlineDeliveryStages.add(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        onlineDeliveryStages.add(STAGE_2_STORE_PROCESSING);
        onlineDeliveryStages.add(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        onlineDeliveryStages.add(OrderStage.STAGE_4_ON_THE_ROAD);
        onlineDeliveryStages.add(OrderStage.STAGE_5_ARRIVED);
        onlineDeliveryStages.add(OrderStage.STAGE_6_WITH_CUSTOMER);
        onlineDeliveryStages.add(OrderStage.STAGE_7_ALL_PAID);

        onlineCollectionStages = new LinkedList<>();
        onlineCollectionStages.add(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        onlineCollectionStages.add(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        onlineCollectionStages.add(STAGE_2_STORE_PROCESSING);
        onlineCollectionStages.add(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        onlineCollectionStages.add(OrderStage.STAGE_6_WITH_CUSTOMER);
        onlineCollectionStages.add(OrderStage.STAGE_7_ALL_PAID);
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

        if (!storeOptional.get().getScheduledDeliveryAllowed() && order.getShippingData().getType() == ShippingData.ShippingType.SCHEDULED_DELIVERY) {
            throw new Exception("Collection or scheduled orders not allowed for " + storeOptional.get().getName());
        }

        if (storeOptional.get().isStoreOffline()) {
            throw new Exception("Shop not available " + storeOptional.get().getName());
        }

        if(order.getOrderType() == ONLINE
                && order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY
                && !storeOptional.get().isDeliverNowAllowed()) {
            throw new Exception("Only Scheduled delivery is allowed at this time");
        }

        List<String> stockItemNames = storeOptional.get()
                .getStockList().stream().map(Stock::getName)
                .collect(Collectors.toList());
        List<String> basketItemNames = order
                .getBasket().getItems().stream().map(BasketItem::getName)
                .collect(Collectors.toList());

        boolean isValidBasketItems = stockItemNames.containsAll(basketItemNames);
        if (!INSTORE.equals(order.getOrderType()) && !isValidBasketItems) {
            throw new Exception("Some basket item are not available in the shop.");
        }

        order.setHasVat(storeOptional.get().getHasVat());
        String orderId = Order.generateId();
        order.setId(orderId);
        order.setStage(STAGE_0_CUSTOMER_NOT_PAID);
        order.setMinimumDepositAllowedPerc(storeOptional.get().getMinimumDepositAllowedPerc());

        double deliveryFee = 0;
        if (order.getOrderType() == OrderType.ONLINE) {
            double distance = calculateDrivingDirectionKM(googleMapsApiKey, order, storeOptional);
            double standardFee = storeOptional.get().getStoreMessenger() != null ? storeOptional.get().getStoreMessenger().getStandardDeliveryPrice() : this.starndardDeliveryFee;
            double standardDistance = storeOptional.get().getStoreMessenger() != null ? storeOptional.get().getStoreMessenger().getStandardDeliveryKm() : this.starndardDeliveryKm;
            double ratePerKM = storeOptional.get().getStoreMessenger() != null ? storeOptional.get().getStoreMessenger().getRatePerKm() : this.ratePerKm;
            deliveryFee = calculateDeliveryFee(standardFee, standardDistance, ratePerKM, distance);
            order.getShippingData().setFee(deliveryFee);
            order.getShippingData().setDistance(distance);
        }

        boolean isEligibleForFreeDelivery = storeOptional.get().isEligibleForFreeDelivery(order);
        order.setFreeDelivery(isEligibleForFreeDelivery);

        if (canChargeServiceFees(storeOptional.get())) {
            order.setServiceFee(serviceFeePerc * (order.getBasketAmount() + (order.getFreeDelivery() ? 0 : deliveryFee)));
        }

        return orderRepo.save(order);
    }

    private boolean canChargeServiceFees(StoreProfile storeProfile) {
        return storeProfile.getStoreType() == StoreType.FOOD || storeProfile.getStoreType() == StoreType.CAR_WASH;
    }

    @Override
    public Order finishOder(Order order) throws Exception {
        validate(order);

        Order persistedOrder = orderRepo.findById(order.getId())
                .orElseThrow(() -> new Exception("Order with id " + order.getId() + " not found."));

        if(order.getStage() != STAGE_0_CUSTOMER_NOT_PAID) {
            return persistedOrder;
        }

        persistedOrder.setDescription(order.getDescription());
        persistedOrder.setPaymentType(order.getPaymentType());
        boolean isDelivery = persistedOrder.getShippingData() != null &&
                persistedOrder.getShippingData().getType() == ShippingData.ShippingType.DELIVERY;

        if (!paymentService.paymentReceived(persistedOrder)) {
            throw new Exception("Payment not cleared yet. please verify again.");
        }

        if (persistedOrder.getOrderType() == INSTORE) {
            if (persistedOrder.getShopPaid()) {
                return order;
            }
            paymentService.completePaymentToShop(persistedOrder);
            persistedOrder.setStage(OrderStage.STAGE_7_ALL_PAID);
            persistedOrder.setShopPaid(true);
        } else {
            persistedOrder.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        }

        //decrease stock available
        Optional<StoreProfile> optional = storeRepository.findById(persistedOrder.getShopId());
        if (optional.isPresent()) {
            StoreProfile store = optional.get();
            Set<Stock> stock = store.getStockList();
            persistedOrder.getBasket()
                    .getItems()
                    .stream()
                    .forEach(item -> {
                        stock.stream()
                                .filter(sto -> sto.getName().equals(item.getName()))
                                .forEach(stockItem -> stockItem.setQuantity(stockItem.getQuantity() - item.getQuantity()));
                    });
            store.setServicesCompleted(store.getServicesCompleted() + 1);
            storeRepository.save(store);

            //notify the shop
            List<Device> shopDevices = deviceRepo.findByUserId(store.getOwnerId());
            if (shopDevices.size() > 0) {
                pushNotificationService.notifyStoreOrderPlaced(shopDevices, persistedOrder);
            } else {
                smsNotificationService.notifyOrderPlaced(store, persistedOrder, userProfileRepo.findById(order.getCustomerId()).get());
            }
            // notify messenger
            if (isDelivery) {
                List<Device> messengerDevices = deviceRepo.findByUserId(persistedOrder.getShippingData().getMessengerId());
                pushNotificationService.notifyMessengerOrderPlaced(messengerDevices, persistedOrder, store);
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

        if (order.getOrderType() == INSTORE || order.getStage() == STAGE_0_CUSTOMER_NOT_PAID
                || order.getStage() == STAGE_7_ALL_PAID) {
            return order;
        }

        switch (order.getShippingData().getType()) {
            case DELIVERY:
                OrderStage stage = onlineDeliveryStages.get(index + 1);
                order.setStage(stage);
                break;
            case SCHEDULED_DELIVERY:
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
                List<Device> devices = order.getShippingData().getType() == ShippingData.ShippingType.SCHEDULED_DELIVERY ?
                        deviceRepo.findByUserId(order.getCustomerId()) :
                        deviceRepo.findByUserId(order.getShippingData().getMessengerId());

                devices.forEach(device -> {
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
                paymentService.completePaymentToShop(order);
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
            case STAGE_6_WITH_CUSTOMER:
                if (!order.getShopPaid()) {
                    paymentService.completePaymentToShop(order);
                }

                if (order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY && !order.getMessengerPaid()) {
                    paymentService.completePaymentToMessenger(order);
                }
                order.setStage(STAGE_7_ALL_PAID);
                break;

        }
        return orderRepo.save(order);
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

    @Scheduled(fixedDelay = 900000, initialDelay = 900000) // 15 minutes
    @Override
    public void cleanUnpaidOrders() {
        Date pastDate = Date.from(LocalDateTime.now()
                .minusMinutes(cleanUpMinutes)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        LOGGER.info("Cleaning up all orders before   s" + pastDate);
        orderRepo.deleteByShopPaidAndStageAndModifiedDateBefore(false, OrderStage.STAGE_0_CUSTOMER_NOT_PAID, pastDate);
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 420000) // 7 minutes
    @Override  //TODO tests
    public void notifyUnpaidOrders() {
        Date pastDate = Date.from(LocalDateTime.now()
                .minusMinutes(7)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        List<Order> unpaidOrders = orderRepo.findByShopPaidAndStageAndModifiedDateBefore(false, STAGE_0_CUSTOMER_NOT_PAID, pastDate);
        unpaidOrders.forEach(order -> {
            for (String admin : adminCellNumbers) {
                try {
                    if(!order.getSmsSentToAdmin()) {
                        smsNotificationService.sendMessage(admin, "Hi, iZinga Admin. Order " + order.getId() + " has not been paid. The customer may be having issues." +
                                "View the order on shop.izinga.co.za/" + order.getShopId() + "/order/" + order.getId() + ".");
                        order.setSmsSentToAdmin(true);
                        orderRepo.save(order);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to sent sms to admin", e);
                }
            }
            LOGGER.info("Sms sent to admin");
        });
    }

    @Override
    public List<Order> findOrderByMessengerId(String id) {
        return orderRepo.findByShippingDataMessengerIdAndStageNot(id, OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }

    @Override
    public List<Order> findAll() {
        return orderRepo.findAll();
    }

    @Scheduled(fixedDelay = 150000, initialDelay = 150000)// 10 minutes
    @Override
    public void checkUnconfirmedOrders() {
        List<Order> orders = orderRepo.findByStage(STAGE_1_WAITING_STORE_CONFIRM);
        LOGGER.info(format("Found %s unconfirmed orders..", orders.size()));
        orders.forEach(order -> {
            Date checkDate = order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY ?
                    Date.from(LocalDateTime.now()
                            .minusMinutes(10)
                            .atZone(ZoneId.systemDefault())
                            .toInstant())
                    : Date.from(LocalDateTime.now()
                    .plusMinutes(60)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());

            //notify by sms
            boolean isLateDeliveryOrder = order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY && order.getModifiedDate().before(checkDate);
            boolean isLateCollectionOrder = order.getShippingData().getType() == ShippingData.ShippingType.SCHEDULED_DELIVERY
                    && order.getShippingData().getPickUpTime().before(checkDate);

            if (isLateDeliveryOrder || isLateCollectionOrder) {
                try {
                    StoreProfile store = storeRepository.findById(order.getShopId()).get();
                    LOGGER.info(format("Order %s is late, call shop at %s", order.getId(), store.getMobileNumber()));
                    if (!order.getSmsSentToShop()) {
                        smsNotificationService.sendMessage(store.getMobileNumber(), "Hello " + store.getName() + ", Please accept the order " + order.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
                        order.setSmsSentToShop(true);
                        LOGGER.info("Sms sent to shop");
                    } else if (!order.getSmsSentToAdmin()) {
                        order.setSmsSentToAdmin(true);
                        for (String number : adminCellNumbers) {
                            smsNotificationService.sendMessage(number, "Hi, iZinga Admin. " + store.getName() + ", has not accepted order " + order.getId() +
                                    " on iZinga app, otherwise the order will be cancelled.");
                        }
                        LOGGER.info("Sms sent to admin");
                    } else {
                        //reverse the transaction
                    }
                    orderRepo.save(order);
                } catch (Exception e) {
                    LOGGER.error("Failed to send sms. ", e);
                }
            }
        });
    }

    private void validate(Order order) throws Exception {
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        if (violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }
}
