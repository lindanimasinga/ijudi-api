package io.curiousoft.izinga.ordermanagement.service;

import co.za.izinga.menuupdater.Application;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.String.format;

@Service
    public class SchedulerService {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);
    private final long cleanUpMinutes;

    private final OrderRepository orderRepo;
    private final StoreRepository storeRepository;
    private final DeviceRepository deviceRepo;
    private final PushNotificationService pushNotificationService;
    private final AdminOnlyNotificationService smsNotificationService;
    private final EmailNotificationService emailNotificationService;
    private final PromotionService promotionService;
    private final UserProfileRepo userProfileRepo;

    public SchedulerService(OrderRepository orderRepo, StoreRepository storeRepository,
                            DeviceRepository deviceRepo, PushNotificationService pushNotificationService,
                            AdminOnlyNotificationService smsNotifcationService,
                            EmailNotificationService emailNotificationService,
                            PromotionService promotionService,
                            @Value("${order.cleanup.unpaid.minutes}") long cleanUpMinutes, UserProfileRepo userProfileRepo) {
        this.orderRepo = orderRepo;
        this.storeRepository = storeRepository;
        this.deviceRepo = deviceRepo;
        this.pushNotificationService = pushNotificationService;
        this.smsNotificationService = smsNotifcationService;
        this.emailNotificationService = emailNotificationService;
        this.cleanUpMinutes = cleanUpMinutes;
        this.promotionService = promotionService;
        this.userProfileRepo = userProfileRepo;
    }

    @Scheduled(fixedDelay = 150000, initialDelay = 150000)// 10 minutes
    public void checkUnconfirmedOrders() {
        List<Order> orders = orderRepo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        LOG.info("Found %s unconfirmed orders..".formatted(orders.size()));
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
                    LOG.info(format("Order %s is late, call shop at %s", order.getId(), store.getMobileNumber()));
                    if (!order.getSmsSentToShop()) {
                        smsNotificationService.sendMessage(store.getMobileNumber(), "Hello " + store.getName() + ", Please accept the order " + order.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
                        order.setSmsSentToShop(true);
                        LOG.info("Sms sent to shop");
                    } else if (!order.getSmsSentToAdmin()) {
                        order.setSmsSentToAdmin(true);
                        emailNotificationService.notifyAdminNewOrder(order);
                        LOG.info("Sms sent to admin");
                    } else {
                        //reverse the transaction
                    }
                    orderRepo.save(order);
                } catch (Exception e) {
                    LOG.error("Failed to send sms. ", e);
                }
            }
        });
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 900000) // 15 minutes
    public void cleanUnpaidOrders() {
        Date pastDate = Date.from(LocalDateTime.now()
                .minusMinutes(cleanUpMinutes)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        LOG.info("Cleaning up all orders before   s%s".formatted(pastDate));
        orderRepo.deleteByShopPaidAndStageAndModifiedDateBefore(false, OrderStage.STAGE_0_CUSTOMER_NOT_PAID, pastDate);
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 420000) // 7 minutes
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
        LOG.info("Sms sent to admin");
    }

    @Scheduled(cron = "* 15 7,10 * * *")// 10 minutes
    public void publishPromosOfTheDay() {
        LOG.info("Finding today's promotions ...");
        var activeUserUserId = orderRepo.findAll()
                .stream()
                .map(Order::getCustomerId)
                .toList();

        var random = new Random();
        var devices = deviceRepo.findByUserIdIn(activeUserUserId);
        var deviceIterator = new LinkedList<>(devices);
        devices.sort((s,b) -> random.nextBoolean() ? 1 : -1);
        var promotions = promotionService.finAllPromotions(StoreType.FOOD)
                .stream()
                .filter(promotion -> !storeRepository.findById(Objects.requireNonNull(promotion.getShopId())).get().isStoreOffline())
                .filter(p -> StringUtils.hasText(p.getTitle()) && StringUtils.hasText(p.getMessage()))
                .sorted((s, b) -> random.nextBoolean() ? 1 : -1)
                .toList();

        promotions.stream()
                .map(promotion -> {
                    var shop = storeRepository.findById(Objects.requireNonNull(promotion.getShopId()));
                    var shopName = shop.map(sh -> StringUtils.hasText(sh.getFranchiseName())? sh.getFranchiseName() : sh.getName()).orElse("");
                    PushHeading heading = new PushHeading();
                    heading.setTitle(format("%s: %s",shopName, promotion.getTitle()));
                    heading.setBody(promotion.getMessage());
                    return new PushMessage(PushMessageType.MARKETING, heading, null);
                })
                .forEachOrdered(pushMessage -> {
                    int numberOfDevicesPerPromo = devices.size()/promotions.size();
                    var filteredDevices = IntStream.range(0, numberOfDevicesPerPromo).mapToObj(i -> deviceIterator.pop()).toList();
                    if (!filteredDevices.isEmpty()) {
                        pushNotificationService.sendNotifications(filteredDevices, pushMessage);
                        LOG.info(format("promotion \"%s\" sent out as push notification", pushMessage.getPushHeading().getTitle()));
                    }
                });
    }

    public void notifyInactiveUsersForLast45Days() {
        LocalDate ninetyDaysAgo = LocalDate.now().minusDays(90);
        var orderesLast90Days = orderRepo.findAllByCreatedDateAfter(ninetyDaysAgo);
        var customerIdLast90Days = orderesLast90Days
                .stream()
                .map(Order::getCustomerId)
                .collect(Collectors.toSet());

        // Find the customers who have placed an order in the last 45 days
        Date fortyFiveDaysAgo = Date.from(LocalDateTime.now().minusDays(45).atZone(ZoneId.systemDefault()).toInstant());
        Set<String> customersInLast45Days = orderesLast90Days.stream()
                .filter(order -> order.getCreatedDate().after(fortyFiveDaysAgo))
                .map(Order::getCustomerId)
                .collect(Collectors.toSet());

        customerIdLast90Days.removeAll(customersInLast45Days);
        var inactiveCustomers45Days = customerIdLast90Days;
        userProfileRepo.findByIdIn(inactiveCustomers45Days);
    }

    @Scheduled(cron = "* 12 5,8,10 * * *")// 10 minutes
    public void publishMenuOfTheDay() throws IOException, InterruptedException {
        Application.main(new String[]{});
    }
}
