package io.curiousoft.izinga.ordermanagement.orders;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import io.curiousoft.izinga.ordermanagement.promocodes.PromoCodeClient;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.*;

import static io.curiousoft.izinga.commons.model.OrderKt.generateId;
import static io.curiousoft.izinga.commons.model.OrderType.INSTORE;
import static io.curiousoft.izinga.commons.model.OrderType.ONLINE;
import static io.curiousoft.izinga.commons.utils.IjudiUtilsKt.*;
import static java.lang.String.format;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final LinkedList<OrderStage> onlineCollectionStages;
    private final LinkedList<OrderStage> onlineDeliveryStages;
    private final OrderRepository orderRepo;
    private final StoreRepository storeRepository;
    private final UserProfileRepo userProfileRepo;
    private final Validator validator;
    private final PaymentService paymentService;
    private final DeviceRepository deviceRepo;
    private final PushNotificationService pushNotificationService;
    private final double starndardDeliveryFee;
    private final double serviceFeePerc;
    private final String googleMapsApiKey;
    private final double starndardDeliveryKm;
    private final double ratePerKm;
    private final PromoCodeClient promoCodeClient;
    private final ApplicationEventPublisher applicationEventPublisher;

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
                            EmailNotificationService emailNotificationService,
                            PromoCodeClient promoCodeClient,
                            ApplicationEventPublisher applicationEventPublisher) {
        this.starndardDeliveryFee = starndardDeliveryFee;
        this.starndardDeliveryKm = starndardDeliveryKm;
        this.ratePerKm = ratePerKm;
        this.serviceFeePerc = serviceFeePerc;
        this.orderRepo = orderRepository;
        this.storeRepository = storeRepository;
        this.userProfileRepo = userProfileRepo;
        this.paymentService = paymentService;
        this.pushNotificationService = pushNotificationService;
        this.deviceRepo = deviceRepo;
        this.googleMapsApiKey = googleMapsApiKey;
        this.promoCodeClient = promoCodeClient;
        this.applicationEventPublisher = applicationEventPublisher;
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
            //if there are orders in progress going to the same location for the same customer and same messenger then add a discount
            List<Order> allOrdersCurrentForCustomer = order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY ?
                    findAllOrdersWithStateForCustomer(order.getCustomerId(), order.getShippingData().getMessengerId(), OrderStage.STAGE_3_READY_FOR_COLLECTION,
                    OrderStage.STAGE_2_STORE_PROCESSING, OrderStage.STAGE_2_STORE_PROCESSING) : List.of();

            // if there are current orders for this user and its same messenger than don't charge a delivery fee.
            double distance = calculateDrivingDirectionKM(googleMapsApiKey, order, storeOptional.get());
            if(allOrdersCurrentForCustomer.isEmpty()) {
                double standardFee = !isNullOrEmpty(storeOptional.get().getStoreMessenger()) ? storeOptional.get().getStandardDeliveryPrice() : this.starndardDeliveryFee;
                double standardDistance = !isNullOrEmpty(storeOptional.get().getStoreMessenger()) ? storeOptional.get().getStandardDeliveryKm() : this.starndardDeliveryKm;
                double ratePerKM = !isNullOrEmpty(storeOptional.get().getStoreMessenger()) ? storeOptional.get().getRatePerKm() : this.ratePerKm;
                deliveryFee = calculateDeliveryFee(standardFee, standardDistance, ratePerKM, distance);
                //if the customer has already paid delivery in the previous orders going the same direction, then
                deliveryFee = deliveryFee - allOrdersCurrentForCustomer.stream().map(o -> o.getShippingData().getFee()).reduce(Double::sum).orElse(0.0);
            }
            order.getShippingData().setFee(deliveryFee);
            order.getShippingData().setDistance(distance);
        }

        boolean isEligibleForFreeDelivery = storeOptional.get().isEligibleForFreeDelivery(order);
        order.setFreeDelivery(isEligibleForFreeDelivery);

        if (canChargeServiceFees(storeOptional.get())) {
            order.setServiceFee(serviceFeePerc * (order.getBasketAmount() + (order.getFreeDelivery() ? 0 : deliveryFee)));
        }

        List<PaymentType> paymentTypes = paymentService.getAllowedPaymentTypes(order.getCustomerId(), storeOptional.get().getStoreType());
        order.setPaymentTypesAllowed(paymentTypes);
        return orderRepo.save(order);
    }

    private List<Order> findAllOrdersWithStateForCustomer(String customerId, String messengerId, OrderStage... stages) {
        return orderRepo.findByCustomerIdAndShippingDataMessengerIdAndStageIn(customerId, messengerId,  stages);
    }

    private boolean canChargeServiceFees(StoreProfile storeProfile) {
        return storeProfile.getStoreType() == StoreType.FOOD || storeProfile.getStoreType() == StoreType.TIPS;
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

        if (!paymentService.paymentReceived(persistedOrder)) {
            throw new Exception("Payment not cleared yet. please verify again.");
        }

        if (persistedOrder.getOrderType() != INSTORE) {
            persistedOrder.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        } else if (persistedOrder.getShopPaid()) {
            return order;
        } else {
            persistedOrder.setStage(OrderStage.STAGE_7_ALL_PAID);
        }

        //decrease stock available
        Optional<StoreProfile> optional = storeRepository.findById(persistedOrder.getShopId());
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
        LOG.info("New order placed. Order No. " + order.getId() + ", Basket Amount. R"+order.getBasketAmount());
        LOG.info("New order placed. Order No. " + order.getId() + ", Delivery Fee. R"+order.getShippingData().getFee());
        NewOrderEvent newOrderEvent = new NewOrderEvent(this, order, persistedOrder.getShippingData().getMessengerId(), store);
        var orderCompleted = orderRepo.save(persistedOrder);
        applicationEventPublisher.publishEvent(newOrderEvent);
        return orderCompleted;
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
    public Order applyPromoCode(String promoCode, Order order) throws Exception {
        var promoData = promoCodeClient.findForUser(order.getId(), order.getCustomerId(), promoCode);
        if (!promoData.verified()) {
            throw new Exception("Promo code not verified.");
        }
        return orderRepo.findById(order.getId())
                .map(ord -> {
                    BasketItem discount = new BasketItem(promoData.promo(),
                            1,
                            promoData.amount(),
                            0);
                    ord.getBasket().getItems().add(discount);
                    orderRepo.save(ord);
                    promoCodeClient.redeemed(promoData);
                    return ord;
                }).orElseThrow();
    }

    @Override
    public List<Order> findOrderByUserId(String userId) {
        return orderRepo.findByCustomerId(userId).orElse(new ArrayList<>());
    }

    @Override
    public List<Order> findOrderByPhone(String phone) throws Exception {
        UserProfile user = Optional.of(userProfileRepo.findByMobileNumber(phone))
                .orElseThrow(() -> new Exception("User not found"));
        return orderRepo.findByCustomerId(user.getId()).orElse(new ArrayList<>());
    }

    @Override
    public List<Order> findOrderByStoreId(String shopId) throws Exception {
        StoreProfile store = storeRepository.findById(shopId)
                .orElseThrow(() -> new Exception("Store not found"));
        return orderRepo.findByShopIdAndStageNot(store.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }

    @Override
    public List<Order> findOrderByMessengerId(String id) {
        return orderRepo.findByShippingDataMessengerIdAndStageNot(id, OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }

    @Override
    public List<Order> findAll() {
        return orderRepo.findAll();
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
