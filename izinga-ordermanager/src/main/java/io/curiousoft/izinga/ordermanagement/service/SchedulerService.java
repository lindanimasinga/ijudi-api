package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.documentmanagement.CloudBucketService;
import io.curiousoft.izinga.documentmanagement.DocumentInfoService;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.shoppinglist.ShoppingListRunEvent;
import io.curiousoft.izinga.ordermanagement.shoppinglist.ShoppingListService;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.recon.payout.PayoutStage;
import io.curiousoft.izinga.recon.payout.PayoutType;
import io.curiousoft.izinga.usermanagement.userconfig.FieldSpec;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigRepo;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.stream.Stream;

import static java.lang.String.format;

@Slf4j
@Service
    public class SchedulerService {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);
    private final long cleanUpMinutes;

    private final OrderRepository orderRepo;
    private final StoreRepository storeRepository;
    private final DeviceRepository deviceRepo;
    private final FirebaseNotificationService pushNotificationService;
    private final io.curiousoft.izinga.messaging.AdminOnlyNotificationService smsNotificationService;
    private final EmailNotificationService emailNotificationService;
    private final PromotionService promotionService;
    private final UserProfileRepo userProfileRepo;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ReconService reconService;
    private final ShoppingListService shoppingListService;
    private final DocumentInfoService documentInfoService;
    private final CloudBucketService cloudBucketService;
    private final UserConfigRepo userConfigRepo;

    public SchedulerService(OrderRepository orderRepo, StoreRepository storeRepository,
                            DeviceRepository deviceRepo, FirebaseNotificationService pushNotificationService,
                            io.curiousoft.izinga.messaging.AdminOnlyNotificationService smsNotifcationService,
                            EmailNotificationService emailNotificationService,
                            PromotionService promotionService,
                            @Value("${order.cleanup.unpaid.minutes}") long cleanUpMinutes,
                            UserProfileRepo userProfileRepo,
                            ApplicationEventPublisher applicationEventPublisher,
                            ReconService reconService,
                            ShoppingListService shoppingListService,
                            DocumentInfoService documentInfoService,
                            CloudBucketService cloudBucketService, UserConfigRepo userConfigRepo) {
        this.orderRepo = orderRepo;
        this.storeRepository = storeRepository;
        this.deviceRepo = deviceRepo;
        this.pushNotificationService = pushNotificationService;
        this.smsNotificationService = smsNotifcationService;
        this.emailNotificationService = emailNotificationService;
        this.cleanUpMinutes = cleanUpMinutes;
        this.promotionService = promotionService;
        this.userProfileRepo = userProfileRepo;
        this.applicationEventPublisher = applicationEventPublisher;
        this.reconService = reconService;
        this.shoppingListService = shoppingListService;
        this.documentInfoService = documentInfoService;
        this.cloudBucketService = cloudBucketService;
        this.userConfigRepo = userConfigRepo;
    }

    @Scheduled(fixedDelay = 600000, initialDelay = 10000)// 10 minutes
    public void checkUnconfirmedOrders() {
        LOG.info("=============== [START] Scheduled Job: checkUnconfirmedOrders ===============");
        long startTime = System.currentTimeMillis();
        int[] counters = {0, 0}; // [successCount, errorCount]

        try {
            List<Order> orders = orderRepo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
            LOG.info("Found {} unconfirmed orders to process", orders == null ? 0 : orders.size());

            if (orders == null || orders.isEmpty()) {
                LOG.warn("No unconfirmed orders found for processing");
            } else {
                orders.forEach(order -> {
                    try {
                        LOG.debug("Processing unconfirmed order: {}", order.getId());
                        Date checkDate = order.getShippingData() != null && order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY ?
                                Date.from(LocalDateTime.now()
                                        .minusMinutes(3)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant())
                                : Date.from(LocalDateTime.now()
                                .plusMinutes(60)
                                .atZone(ZoneId.systemDefault())
                                .toInstant());

                        boolean isLateDeliveryOrder = order.getShippingData() != null && order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY &&
                                order.getModifiedDate() != null && order.getModifiedDate().before(checkDate);
                        boolean isLateCollectionOrder = order.getShippingData() != null && order.getShippingData().getType() == ShippingData.ShippingType.SCHEDULED_DELIVERY
                                && order.getShippingData().getPickUpTime() != null && order.getShippingData().getPickUpTime().before(checkDate);

                        if (isLateDeliveryOrder || isLateCollectionOrder) {
                            try {
                                StoreProfile store = storeRepository.findById(order.getShopId()).orElse(null);
                                if (store != null) {
                                    LOG.info(format("Order %s is late, notifying shop at %s", order.getId(), store.getMobileNumber()));
                                    if (!order.getSmsSentToShop()) {
                                        LOG.info("Sending SMS to shop for order: {}", order.getId());
                                        smsNotificationService.sendMessage(store.getMobileNumber(), "Hello " + store.getName() + ", Please accept the order " + order.getId() +
                                                " on iZinga app, otherwise the order will be cancelled.");
                                        order.setSmsSentToShop(true);
                                        LOG.info("SMS successfully sent to shop for order: {}", order.getId());
                                        counters[0]++;
                                    } else if (!order.getSmsSentToAdmin()) {
                                        LOG.info("Notifying admin about unconfirmed order: {}", order.getId());
                                        order.setSmsSentToAdmin(true);
                                        emailNotificationService.notifyAdminNewOrder(order);
                                        LOG.info("Admin notification sent for order: {}", order.getId());
                                        counters[0]++;
                                    } else {
                                        LOG.warn("Order {} already has notifications sent, skipping", order.getId());
                                    }
                                    orderRepo.save(order);
                                }
                            } catch (Exception e) {
                                counters[1]++;
                                LOG.error("Failed to process late order: {}", order.getId(), e);
                            }
                        }
                    } catch (Exception e) {
                        counters[1]++;
                        LOG.error("Error processing order in forEach: {}", order.getId(), e);
                    }
                });
            }
        } catch (Exception e) {
            counters[1]++;
            LOG.error("Fatal error in checkUnconfirmedOrders job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: checkUnconfirmedOrders ===============");
            LOG.info("Job Statistics - Successful: {}, Errors: {}, Duration: {}ms", counters[0], counters[1], duration);
        }
    }

    @Scheduled(fixedDelay = 600000, initialDelay = 20000)// 10 minutes
    public void checkScheduledOrders() {
        LOG.info("=============== [START] Scheduled Job: checkScheduledOrders ===============");
        long startTime = System.currentTimeMillis();
        int[] counters = {0, 0}; // [processedCount, errorCount]

        try {
            List<Order> allOrders = orderRepo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
            List<Order> orders = allOrders != null ?
                    allOrders.stream()
                    .filter(order -> order.getShippingData() != null && ShippingData.ShippingType.SCHEDULED_DELIVERY == order.getShippingData().getType())
                    .toList()
                    : new java.util.ArrayList<>();

            LOG.info("Found {} unconfirmed scheduled orders to process", orders.size());

            if (orders.isEmpty()) {
                LOG.warn("No unconfirmed scheduled orders found for processing");
                return;
            }

            orders.forEach(order -> {
                try {
                    LOG.debug("Processing scheduled order: {}", order.getId());
                    Date checkDate = Date.from(LocalDateTime.now()
                            .plusMinutes(60)
                            .atZone(ZoneId.systemDefault())
                            .toInstant());

                    boolean sendNewOrderNotification = order.getShippingData() != null &&
                            order.getShippingData().getType() == ShippingData.ShippingType.SCHEDULED_DELIVERY
                            && order.getShippingData().getPickUpTime() != null &&
                            order.getShippingData().getPickUpTime().before(checkDate);

                    if (sendNewOrderNotification) {
                        LOG.info("Publishing NewOrderEvent for scheduled order: {}", order.getId());
                        var store = storeRepository.findById(order.getShopId());
                        if (store.isPresent()) {
                            NewOrderEvent newOrderEvent = new NewOrderEvent(this, order, order.getShippingData().getMessengerId(), store.get());
                            applicationEventPublisher.publishEvent(newOrderEvent);
                            LOG.info("NewOrderEvent successfully published for order: {}", order.getId());
                            counters[0]++;
                        }
                    } else {
                        LOG.debug("Order {} pickup time not yet within 60 minute window, skipping", order.getId());
                    }
                } catch (Exception e) {
                    counters[1]++;
                    LOG.error("Failed to process scheduled order: {}", order.getId(), e);
                }
            });
        } catch (Exception e) {
            counters[1]++;
            LOG.error("Fatal error in checkScheduledOrders job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: checkScheduledOrders ===============");
            LOG.info("Job Statistics - Processed: {}, Errors: {}, Duration: {}ms", counters[0], counters[1], duration);
        }
    }

    //runs every day at 8am to send welcome message to new drivers who registered but not yet approved
    @Scheduled(cron = "0 15 8 * * *")// 10 minutes
    public void newDrivers() {
        LOG.info("=============== [START] Scheduled Job: newDrivers ===============");
        long startTime = System.currentTimeMillis();
        int[] counters = {0, 0}; // [successCount, errorCount]

        try {
            LOG.info("Finding new drivers to send welcome message..");
            var userConfig = userConfigRepo.findAll();
            LOG.info("Found {} user configurations", userConfig.size());

            var driver = userProfileRepo.findByProfileApproved(false);
            LOG.info("Found {} unapproved driver profiles", driver.size());

            var messengerDrivers = driver.stream()
                    .filter(dr -> dr.getRole() == ProfileRoles.MESSENGER)
                    .toList();
            LOG.info("Processing {} messenger drivers", messengerDrivers.size());

            messengerDrivers.forEach(profile -> {
                try {
                    LOG.debug("Processing driver profile: {} ({})", profile.getName(), profile.getId());
                    LOG.info("Sending welcome message to driver {} with mobile {}",  profile.getName(), profile.getMobileNumber());

                    var consentSent = (profile.getCrminalCheckData() != null && profile.getCrminalCheckData().getCriminalCheckMessageSent());
                    if (!consentSent) {
                        LOG.info("Sending criminal check consent message to driver: {}", profile.getName());
                        smsNotificationService.sendCrimnalCheckConsent(profile.getMobileNumber(), profile.getName());
                        CriminalCheckData data = new CriminalCheckData();
                        data.setCriminalCheckMessageSent(true);
                        profile.setCrminalCheckData(data);
                        LOG.info("Criminal check consent message sent to driver: {}", profile.getName());
                    } else {
                        LOG.debug("Criminal check consent already sent for driver: {}", profile.getName());
                    }

                    //check missing required documents and send reminder if any
                    var allFieldsProvided = userConfig.stream()
                            .filter(it -> it.getLabel().equals(profile.getDescription()))
                            .flatMap(config -> Stream.of(config.getMandatoryFields().toArray(FieldSpec[]::new)))
                            .allMatch(it -> profile.getTag().get(it.getName()) != null);

                    if (!allFieldsProvided && (profile.getMissingDocumentsReminderSent() == null || !profile.getMissingDocumentsReminderSent())) {
                        LOG.info("Sending missing documents reminder to driver: {}", profile.getName());
                        smsNotificationService.sendMissingDocumentReminder(profile.getMobileNumber(), profile.getName());
                        profile.setMissingDocumentsReminderSent(true);
                        LOG.info("Missing documents reminder sent to driver: {}", profile.getName());
                    } else if (allFieldsProvided) {
                        LOG.debug("All required documents provided for driver: {}", profile.getName());
                    } else {
                        LOG.debug("Missing documents reminder already sent for driver: {}", profile.getName());
                    }

                    userProfileRepo.save(profile);
                    LOG.debug("Driver profile saved successfully: {}", profile.getName());
                    counters[0]++;
                } catch (Exception e) {
                    counters[1]++;
                    LOG.error("Failed to send welcome message to driver: {}", profile.getName(), e);
                }
            });

            LOG.info("Welcome message processing completed for new drivers");
        } catch (Exception e) {
            counters[1]++;
            LOG.error("Fatal error in newDrivers job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: newDrivers ===============");
            LOG.info("Job Statistics - Successful: {}, Errors: {}, Duration: {}ms", counters[0], counters[1], duration);
        }
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 900000) // 15 minutes
    public void cleanUnpaidOrders() {
        LOG.info("=============== [START] Scheduled Job: cleanUnpaidOrders ===============");
        long startTime = System.currentTimeMillis();

        try {
            Date pastDate = Date.from(LocalDateTime.now()
                    .minusMinutes(cleanUpMinutes)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
            LOG.info("Cleaning up all unpaid orders before: {}", pastDate);
            LOG.info("Cleanup threshold: {} minutes", cleanUpMinutes);

            orderRepo.deleteByShopPaidAndStageAndModifiedDateBefore(false, OrderStage.STAGE_0_CUSTOMER_NOT_PAID, pastDate);
            LOG.info("Successfully cleaned up unpaid orders");
        } catch (Exception e) {
            LOG.error("Fatal error in cleanUnpaidOrders job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: cleanUnpaidOrders ===============");
            LOG.info("Job Duration: {}ms", duration);
        }
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 420000) // 7 minutes
    public void notifyUnpaidOrders() {
        LOG.info("=============== [START] Scheduled Job: notifyUnpaidOrders ===============");
        long startTime = System.currentTimeMillis();
        int[] counters = {0, 0}; // [notifiedCount, errorCount]

        try {
            Date pastDate = Date.from(LocalDateTime.now()
                    .minusMinutes(7)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
            LOG.info("Fetching unpaid orders before: {}", pastDate);

            List<Order> unpaidOrders = orderRepo.findByShopPaidAndStageAndModifiedDateBefore(false, OrderStage.STAGE_0_CUSTOMER_NOT_PAID, pastDate);
            LOG.info("Found {} unpaid orders to process", unpaidOrders.size());

            unpaidOrders.forEach(order -> {
                try {
                    LOG.debug("Processing unpaid order: {}", order.getId());
                    if (!order.getSmsSentToAdmin()) {
                        LOG.info("Sending admin notification for unpaid order: {}", order.getId());
                        emailNotificationService.notifyAdminOrderNotPaid(order);
                        order.setSmsSentToAdmin(true);
                        orderRepo.save(order);
                        LOG.info("Admin notification sent for order: {}", order.getId());
                        counters[0]++;
                    } else {
                        LOG.debug("Admin already notified for order: {}", order.getId());
                    }
                } catch (Exception e) {
                    counters[1]++;
                    LOG.error("Failed to notify admin for unpaid order: {}", order.getId(), e);
                }
            });
        } catch (Exception e) {
            counters[1]++;
            LOG.error("Fatal error in notifyUnpaidOrders job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: notifyUnpaidOrders ===============");
            LOG.info("Job Statistics - Notified: {}, Errors: {}, Duration: {}ms", counters[0], counters[1], duration);
        }
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 480000) // 7 minutes
    public void updateMissingPayouts() {
        LOG.info("=============== [START] Scheduled Job: updateMissingPayouts ===============");
        long startTime = System.currentTimeMillis();
        int[] counters = {0, 0, 0}; // [shopPayoutsGenerated, messengerPayoutsGenerated, errorCount]

        try {
            Date fromDate = Date.from(LocalDateTime.now()
                    .minusHours(720)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
            Date toDate = Date.from(LocalDateTime.now()
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
            LOG.info("Fetching unpaid orders from {} to {}", fromDate, toDate);

            List<Order> unpaidOrders = orderRepo.findByShopPaidAndStageAndPayoutCreatedAndCreatedDateAfter(false,
                    OrderStage.STAGE_7_ALL_PAID, false, fromDate);
            LOG.info("Found {} unpaid orders to check for missing payouts", unpaidOrders.size());

            unpaidOrders.stream()
                    .filter(it -> !it.getPayoutCreated())
                    .forEach(order -> {
                        try {
                            LOG.debug("Processing payout for order: {}", order.getId());

                            var payoutCreatedForShop = reconService.getAllPayouts(PayoutType.SHOP,
                                            fromDate,
                                            toDate,
                                            order.getShopId())
                                    .stream()
                                    .anyMatch(it -> it.getToId().equals(order.getShopId()) && it.getPayoutStage() == PayoutStage.PENDING);

                            var payoutCreatedForMessenger = order.getShippingData().getMessengerId() != null && reconService.getAllPayouts(PayoutType.MESSENGER,
                                            fromDate,
                                            toDate,
                                            order.getShippingData().getMessengerId())
                                    .stream()
                                    .anyMatch(it -> it.getToId().equals(order.getShopId()) && it.getPayoutStage() == PayoutStage.PENDING);

                            if (!payoutCreatedForShop) {
                                LOG.info("Generating missing shop payout for order: {}", order.getId());
                                reconService.generatePayoutForShopAndOrder(order);
                                LOG.info("Shop payout generated for order: {}", order.getId());
                                counters[0]++;
                            } else {
                                LOG.debug("Shop payout already exists for order: {}", order.getId());
                            }

                            if (!payoutCreatedForMessenger) {
                                LOG.info("Generating missing messenger payout for order: {}", order.getId());
                                reconService.generatePayoutForMessengerAndOrder(order);
                                LOG.info("Messenger payout generated for order: {}", order.getId());
                                counters[1]++;
                            } else {
                                LOG.debug("Messenger payout already exists for order: {}", order.getId());
                            }

                            order.setPayoutCreated(true);
                            orderRepo.save(order);
                            LOG.debug("Order marked as payout created: {}", order.getId());
                        } catch (Exception e) {
                            counters[2]++;
                            LOG.error("Failed to process payout for order: {}", order.getId(), e);
                        }
                    });
        } catch (Exception e) {
            counters[2]++;
            LOG.error("Fatal error in updateMissingPayouts job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: updateMissingPayouts ===============");
            LOG.info("Job Statistics - Shop Payouts Generated: {}, Messenger Payouts Generated: {}, Errors: {}, Duration: {}ms",
                    counters[0], counters[1], counters[2], duration);
        }
    }

/*    @Scheduled(cron = "* 15 6,8 * * *")// 10 minutes
    public void buildRecommendations() {
        LOG.info("Finding all order ...");
        var orderItemCounts = orderRepo.findAll()
                .stream()
                .flatMap(order -> {
                    var items = order.getBasket().getItems();
                    items.forEach(it -> it.setCustomerId(order.getCustomerId()));
                    return items.stream();
                })
                .collect(Collectors.groupingBy(
                        item -> new AbstractMap.SimpleEntry<>(item.getCustomerId(), item.getName()),
                        Collectors.counting()));

        orderItemCounts.forEach((key, count) -> {
            String customerId = key.getKey();
            String itemName = key.getValue();
            LOG.info("Customer: {}, Item: {}, Count: {}", customerId, itemName, count);
        });
    }*/

    @Scheduled(cron = "* 15 8,9 * * *")// 10 minutes
    public void findShoppingListsToAction() {
        LOG.info("=============== [START] Scheduled Job: findShoppingListsToAction ===============");
        long startTime = System.currentTimeMillis();
        int[] counters = {0, 0}; // [publishedCount, errorCount]

        try {
            Date dateFrom = Date.from(LocalDateTime.now()
                            .withHour(0)
                            .withMinute(0)
                            .withSecond(0)
                            .minusMinutes(1)
                            .atZone(ZoneId.systemDefault())
                            .toInstant());
            Date dateTo = Date.from(LocalDateTime.now()
                            .plusDays(1)
                            .withMinute(0)
                            .withSecond(0)
                            .withHour(0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant());
            LOG.info("Finding all shopping lists from {} to {}", dateFrom, dateTo);

            var shoppingLists = shoppingListService.getShoppingListsScheduledBetween(dateFrom, dateTo);
            LOG.info("Found {} shopping lists to process", shoppingLists.size());

            shoppingLists.forEach(shoppingList -> {
                try {
                    LOG.info("Processing shopping list: {} - Next Run Date: {}", shoppingList.getId(), shoppingList.getName());
                    applicationEventPublisher.publishEvent(new ShoppingListRunEvent(this, shoppingList));
                    LOG.info("ShoppingListRunEvent published for: {}", shoppingList.getName());
                    counters[0]++;
                } catch (Exception e) {
                    counters[1]++;
                    LOG.error("Failed to publish event for shopping list: {}", shoppingList.getName(), e);
                }
            });
        } catch (Exception e) {
            counters[1]++;
            LOG.error("Fatal error in findShoppingListsToAction job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: findShoppingListsToAction ===============");
            LOG.info("Job Statistics - Published: {}, Errors: {}, Duration: {}ms", counters[0], counters[1], duration);
        }
    }

    @Scheduled(cron = "* 15 7,10 * * *")// 10 minutes
    public void publishPromosOfTheDay() {
        LOG.info("=============== [START] Scheduled Job: publishPromosOfTheDay ===============");
        long startTime = System.currentTimeMillis();
        int[] counters = {0, 0}; // [promosSent, errorCount]

        try {
            LOG.info("Finding today's promotions ...");
            var activeUserUserId = orderRepo.findAll()
                    .stream()
                    .map(Order::getCustomerId)
                    .toList();
            LOG.info("Found {} active users", activeUserUserId.size());

            var random = new Random();
            var devices = deviceRepo.findByUserIdIn(activeUserUserId);
            LOG.info("Found {} devices to send promotions to", devices.size());

            var deviceIterator = new LinkedList<>(devices);
            devices.sort((s,b) -> random.nextBoolean() ? 1 : -1);

            var promotions = promotionService.finAllPromotions(StoreType.FOOD)
                    .stream()
                    .filter(promotion -> !storeRepository.findById(Objects.requireNonNull(promotion.getShopId())).get().isStoreOffline())
                    .filter(p -> StringUtils.hasText(p.getTitle()) && StringUtils.hasText(p.getMessage()))
                    .sorted((s, b) -> random.nextBoolean() ? 1 : -1)
                    .toList();
            LOG.info("Found {} active promotions to process", promotions.size());

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
                        try {
                            int numberOfDevicesPerPromo = devices.size()/promotions.size();
                            var filteredDevices = IntStream.range(0, numberOfDevicesPerPromo).mapToObj(i -> deviceIterator.pop()).toList();
                            if (!filteredDevices.isEmpty()) {
                                LOG.info("Sending promotion to {} devices: {}", filteredDevices.size(), pushMessage.getPushHeading().getTitle());
                                pushNotificationService.sendNotifications(filteredDevices, pushMessage);
                                LOG.info("Promotion sent out as push notification: {} to {} devices", pushMessage.getPushHeading().getTitle(), filteredDevices.size());
                                counters[0]++;
                            }
                        } catch (Exception e) {
                            counters[1]++;
                            LOG.error("Failed to send promotion: {}", pushMessage.getPushHeading().getTitle(), e);
                        }
                    });
        } catch (Exception e) {
            counters[1]++;
            LOG.error("Fatal error in publishPromosOfTheDay job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: publishPromosOfTheDay ===============");
            LOG.info("Job Statistics - Promotions Sent: {}, Errors: {}, Duration: {}ms", counters[0], counters[1], duration);
        }
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

    @Scheduled(fixedDelay = 10000000L , initialDelay = 5000)// 10 minutes
    public void publishMenuOfTheDay() throws IOException, InterruptedException {
     //  Application.main(new String[]{});
    }

    //every 3am check for stores with flag generate missing images
    @Scheduled(fixedDelay = 600000)
    public void generateMissingImagesForStores() throws IOException, InterruptedException {
        LOG.info("=============== [START] Scheduled Job: generateMissingImagesForStores ===============");
        long startTime = System.currentTimeMillis();
        int[] counters = {0, 0, 0}; // [storesProcessed, imagesGenerated, errorCount]

        try {
            var storesWithMissingImages = storeRepository.findByGenerateMissingImagesTrue();
            LOG.info("Found {} stores with missing images flag enabled", storesWithMissingImages.size());

            for (var store : storesWithMissingImages) {
                try {
                    LOG.info("Processing store: {}", store.getName());
                    int storeImageCount = 0;

                    for (var stock : store.getStockList()) {
                        try {
                            if (stock.getImages() == null || stock.getImages().isEmpty()) {
                                LOG.info("Generating image for stock item: {} in store: {}", stock.getName(), store.getName());

                                var urls = documentInfoService.createImage(
                                        "Create a photorealistic, high-quality image of a " + stock.getName() +
                                                ". Details include: " + (stock.getDescription() != null ? stock.getDescription() : "no description")
                                                + ". No text, no logos, no watermarks. Professional commercial photography style, suitable for a TV advertisement. " +
                                                "Natural lighting, shallow depth of field, sharp focus, premium composition. " +
                                                "Clean background, realistic textures, studio-quality color grading.",
                                        1,
                                        "256x256")
                                        .stream()
                                        .map(url -> {
                                            LOG.debug("Uploading image to AWS from URL: {}", url);
                                            try {
                                                var fileName = url.substring(url.lastIndexOf("/") + 1);
                                                var fileBytes = createMultipartFileFromUrl(url);
                                                cloudBucketService.putObject(fileName, fileBytes);
                                                String awsUrl = cloudBucketService.getUrl(fileName).toString();
                                                LOG.info("Image successfully uploaded to AWS: {}", awsUrl);
                                                return awsUrl;
                                            } catch (Exception e) {
                                                LOG.error("Failed to download or upload image from URL: {}", url, e);
                                            }
                                            return null;
                                        }).collect(Collectors.toList());

                                stock.setImages(urls);
                                storeRepository.save(store);
                                LOG.info("Image generated and saved for stock item: {}", stock.getName());
                                storeImageCount++;
                                counters[1]++;
                            } else {
                                LOG.debug("Stock item {} already has images, skipping", stock.getName());
                            }
                            Thread.sleep(12000);
                        } catch (Exception e) {
                            counters[2]++;
                            LOG.error("Failed to generate image for stock item: {} in store: {}", stock.getName(), store.getName(), e);
                        }
                    }

                    LOG.info("Completed processing store: {} - Images generated: {}", store.getName(), storeImageCount);
                    counters[0]++;
                } catch (Exception e) {
                    counters[2]++;
                    LOG.error("Failed to process store: {}", store.getName(), e);
                }
            }
        } catch (Exception e) {
            counters[2]++;
            LOG.error("Fatal error in generateMissingImagesForStores job", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("=============== [FINISH] Scheduled Job: generateMissingImagesForStores ===============");
            LOG.info("Job Statistics - Stores Processed: {}, Images Generated: {}, Errors: {}, Duration: {}ms",
                    counters[0], counters[1], counters[2], duration);
        }
    }

    public byte[] createMultipartFileFromUrl(String imageUrl) throws IOException {
        java.net.URL url = new java.net.URL(imageUrl);
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        return connection.getInputStream().readAllBytes();
    }

}





