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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static java.lang.String.format;

@Service
public class SchedulerService {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);
    private final long cleanUpMinutes;

    private OrderRepository orderRepo;
    private StoreRepository storeRepository;
    private UserProfileRepo userProfileRepo;
    private PaymentService paymentService;
    private DeviceRepository deviceRepo;
    private PushNotificationService pushNotificationService;
    private AdminOnlyNotificationService smsNotificationService;
    private EmailNotificationService emailNotificationService;
    private final List<String> adminCellNumbers;
    private PromotionService promotionService;

    public SchedulerService(OrderRepository orderRepo, StoreRepository storeRepository,
                            UserProfileRepo userProfileRepo, PaymentService paymentService,
                            DeviceRepository deviceRepo, PushNotificationService pushNotificationService,
                            AdminOnlyNotificationService smsNotifcationService,
                            EmailNotificationService emailNotificationService,
                            PromotionService promotionService,
                            @Value("${order.cleanup.unpaid.minutes}") long cleanUpMinutes,
                            @Value("${admin.cellNumber}") List<String> adminCellNumbers) {
        this.orderRepo = orderRepo;
        this.storeRepository = storeRepository;
        this.userProfileRepo = userProfileRepo;
        this.paymentService = paymentService;
        this.deviceRepo = deviceRepo;
        this.pushNotificationService = pushNotificationService;
        this.smsNotificationService = smsNotifcationService;
        this.emailNotificationService = emailNotificationService;
        this.cleanUpMinutes = cleanUpMinutes;
        this.adminCellNumbers = adminCellNumbers;
        this.promotionService = promotionService;
    }

    @Scheduled(fixedDelay = 150000, initialDelay = 150000)// 10 minutes
    public void checkUnconfirmedOrders() {
        List<Order> orders = orderRepo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        LOG.info(format("Found %s unconfirmed orders..", orders.size()));
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
        LOG.info("Cleaning up all orders before   s" + pastDate);
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

    @Scheduled(cron = "* 30 6,10 * * *")// 10 minutes
    public void publishPromosOfTheDay() {
        LOG.info("Finding today's promotions ...");
        var activeUserUserId = orderRepo.findAll()
                .stream()
                .map(Order::getCustomerId)
                .toList();
        var devices = deviceRepo.findByUserIdIn(activeUserUserId);
        promotionService.finAllPromotions(StoreType.FOOD).stream()
                .filter(p -> StringUtils.hasText(p.getTitle()) && StringUtils.hasText(p.getMessage()))
                .filter(p -> new Random().nextBoolean())
                .limit(1)
                .map(promotion -> {
                    var shopName = storeRepository.findById(Objects.requireNonNull(promotion.getShopId()))
                            .map(sh -> StringUtils.hasText(sh.getFranchiseName())? sh.getFranchiseName() : sh.getName()).orElse("");
                    PushHeading heading = new PushHeading();
                    heading.setTitle(format("%s: %s",shopName, promotion.getTitle()));
                    heading.setBody(promotion.getMessage());
                    return new PushMessage(PushMessageType.MARKETING, heading, null);
                })
                .forEach(pushMessage -> {
                    pushNotificationService.sendNotifications(devices, pushMessage);
                    LOG.info(format("promotion \"%s\" sent out as push notification", pushMessage.getPushHeading().getTitle()));
                });
    }
}
