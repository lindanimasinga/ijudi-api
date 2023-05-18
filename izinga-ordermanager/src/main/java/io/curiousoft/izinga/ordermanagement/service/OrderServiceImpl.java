package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.OrderRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
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

import static io.curiousoft.izinga.commons.model.OrderKt.generateId;
import static io.curiousoft.izinga.commons.model.OrderType.INSTORE;
import static io.curiousoft.izinga.commons.model.OrderType.ONLINE;
import static io.curiousoft.izinga.commons.utils.IjudiUtilsKt.*;
import static java.lang.String.format;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

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
    private final EmailNotificationService emailNotificationService;
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
                            AdminOnlyNotificationService smsNotifcationService,
                            EmailNotificationService emailNotificationService) {
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
        this.emailNotificationService = emailNotificationService;
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
        onlineDeliveryStages.add(OrderStage.STAGE_7_ALL_PAID);

        onlineCollectionStages = new LinkedList<>();
        onlineCollectionStages.add(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        onlineCollectionStages.add(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        onlineCollectionStages.add(OrderStage.STAGE_2_STORE_PROCESSING);
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
                .getStockList().stream().map(Stock::getName).toList();
        List<String> basketItemNames = order
                .getBasket().getItems().stream().map(BasketItem::getName).toList();

        boolean isValidBasketItems = stockItemNames.containsAll(basketItemNames);
        if (!INSTORE.equals(order.getOrderType()) && !isValidBasketItems) {
            throw new Exception("Some basket item are not available in the shop.");
        }

        order.setHasVat(storeOptional.get().getHasVat());
        String orderId = generateId();
        order.setId(orderId);
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setMinimumDepositAllowedPerc(storeOptional.get().getMinimumDepositAllowedPerc());

        double deliveryFee = 0;
        if (order.getOrderType() == OrderType.ONLINE) {
            double distance = calculateDrivingDirectionKM(googleMapsApiKey, order, storeOptional.get());
            double standardFee = !isNullOrEmpty(storeOptional.get().getStoreMessenger()) ? storeOptional.get().getStandardDeliveryPrice() : this.starndardDeliveryFee;
            double standardDistance = !isNullOrEmpty(storeOptional.get().getStoreMessenger()) ? storeOptional.get().getStandardDeliveryKm() : this.starndardDeliveryKm;
            double ratePerKM = !isNullOrEmpty(storeOptional.get().getStoreMessenger()) ? storeOptional.get().getRatePerKm() : this.ratePerKm;
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

        if(order.getStage() != OrderStage.STAGE_0_CUSTOMER_NOT_PAID) {
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
            persistedOrder.setStage(OrderStage.STAGE_7_ALL_PAID);
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
                    .forEach(item -> {
                        stock.stream()
                                .filter(sto -> sto.getName().equals(item.getName()))
                                .findFirst()
                                .ifPresent(stockItem -> {
                                    stockItem.setQuantity(stockItem.getQuantity() - item.getQuantity());
                                    if(store.getStoreWebsiteUrl() != null) {
                                        String fullUrl = (store.getStoreWebsiteUrl() + "/" + stockItem.getExternalUrlPath())
                                                .replaceAll("(/){2,}", "/").replaceAll(":/", "://");
                                        item.setExternalUrl(fullUrl);
                                    }
                                });
                    });
            store.setServicesCompleted(store.getServicesCompleted() + 1);
            storeRepository.save(store);

            //notify the shop
            List<Device> shopDevices = deviceRepo.findByUserId(store.getOwnerId());
            if (shopDevices.size() > 0) {
                pushNotificationService.notifyStoreOrderPlaced(store.getName(), shopDevices, persistedOrder);
            } else {
                smsNotificationService.notifyOrderPlaced(store, persistedOrder, userProfileRepo.findById(order.getCustomerId()).get());
            }
            // notify messenger
            if (isDelivery) {
                List<Device> messengerDevices = deviceRepo.findByUserId(persistedOrder.getShippingData().getMessengerId());
                pushNotificationService.notifyMessengerOrderPlaced(messengerDevices, persistedOrder, store);
            }
        }
        LOGGER.info("New order placed. Order No. " + order.getId() + ", Basket Amount. R"+order.getBasketAmount());
        LOGGER.info("New order placed. Order No. " + order.getId() + ", Delivery Fee. R"+order.getShippingData().getFee());
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

        if (order.getOrderType() == INSTORE || order.getStage() == OrderStage.STAGE_0_CUSTOMER_NOT_PAID
                || order.getStage() == OrderStage.STAGE_7_ALL_PAID) {
            return order;
        }

        if(order.getStage() == OrderStage.CANCELLED) throw new Exception("Order with id " + orderId + " has been cancelled");

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
                order.setStage(OrderStage.STAGE_7_ALL_PAID);
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
        List<Order> unpaidOrders = orderRepo.findByShopPaidAndStageAndModifiedDateBefore(false, OrderStage.STAGE_0_CUSTOMER_NOT_PAID, pastDate);
        unpaidOrders.forEach(order -> {
                    if (!order.getSmsSentToAdmin()) {
                        emailNotificationService.notifyAdminOrderNotPaid(order);
                        order.setSmsSentToAdmin(true);
                        orderRepo.save(order);
                    }
                });
            LOGGER.info("Sms sent to admin");
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
        List<Order> orders = orderRepo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        LOGGER.info(format("Found %s unconfirmed orders..", orders.size()));
        orders.forEach(order -> {
            Date checkDate = order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY ?
                    Date.from(LocalDateTime.now()
                            .minusMinutes(3)
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
                        emailNotificationService.notifyAdminNewOrder(order);
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

    @Override
    public Order cancelOrder(String id) throws Exception {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new Exception("Order with id " + id + " not found."));

        if(order.getStage() == OrderStage.STAGE_6_WITH_CUSTOMER || order.getStage() == OrderStage.STAGE_7_ALL_PAID) {
            throw  new Exception("Cannot cancel this order. Please cancel manually.");
        }
        boolean successful = paymentService.reversePayment(order);
        if(!successful) throw  new Exception("Unable to reserve payment. Please reverse manually.");

        order.setStage(OrderStage.CANCELLED);
        order = orderRepo.save(order);
        List<Device> customerDevices = deviceRepo.findByUserId(order.getCustomerId());
        PushHeading pushMessage = new PushHeading("Your order has been cancelled. Payment has been reversed to your account.",
                "Your order has been cancelled.", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, pushMessage, order);
        customerDevices.forEach(device -> {
            try {
                pushNotificationService.sendNotification(device, message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return order;
    }

    private void validate(Order order) throws Exception {
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        if (violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }
}
