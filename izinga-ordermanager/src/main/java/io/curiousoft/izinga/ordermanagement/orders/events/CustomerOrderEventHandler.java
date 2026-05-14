package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.events.OrderCancelledEvent;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class CustomerOrderEventHandler implements OrderEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerOrderEventHandler.class);

    private final FirebaseNotificationService pushNotificationService;
    private final EmailNotificationService emailNotificationService;
    private final WhatsappNotificationService whatsappNotificationService;
    private final DeviceService deviceService;
    private final UserProfileService userProfileService;

    CustomerOrderEventHandler(FirebaseNotificationService pushNotificationService,
                              EmailNotificationService emailNotificationService,
                              WhatsappNotificationService whatsappNotificationService,
                              DeviceService deviceService,
                              UserProfileService userProfileService) {
            this.pushNotificationService = pushNotificationService;
            this.emailNotificationService = emailNotificationService;
            this.whatsappNotificationService = whatsappNotificationService;
            this.deviceService = deviceService;
            this.userProfileService = userProfileService;
    }

    @Async
    @EventListener
    @Override
    public void handleNewOrderEvent(NewOrderEvent newOrderEvent) throws IOException {
        var store = newOrderEvent.getReceivingStore();
        var order = newOrderEvent.getOrder();
        var customer = userProfileService.find(order.getCustomerId());
        List<Device> customerDevices = deviceService.findByUserId(customer.getId());
        if (!customerDevices.isEmpty()) {
            pushNotificationService.notifyStoreOrderPlaced(store.getName(), customerDevices, order);
        }
    }

    @Async
    @EventListener
    @Override
    public void handleNewOrderEventToEmail(NewOrderEvent event) throws Exception {

    }

    @Async
    @EventListener
    @Override
    public void handleNewOrderEventToWhatsapp(NewOrderEvent event) throws Exception {
        var order = event.getOrder();
        var customer = userProfileService.find(order.getCustomerId());
        whatsappNotificationService.notifyOrderPlaced(order, customer);
    }

    @Override
    public void handleOrderUpdatedEvent(OrderUpdatedEvent orderUpdatedEvent) {
        var order = orderUpdatedEvent.getOrder();
        var store = orderUpdatedEvent.getReceivingStore();
        final String order_status_updated = "Order Status Updated";
        var originalStage = order.getStage();
        PushHeading title = null;
        PushMessage message = null;
        switch (originalStage) {
            case STAGE_0_CUSTOMER_NOT_PAID, STAGE_1_WAITING_STORE_CONFIRM:
                return;
            case STAGE_2_STORE_PROCESSING:
                //notify only customer
                var body = store.getStoreType() == StoreType.MOVERS ? "The driver is coming to your pickup location:" + order.getId() : "The store has started processing your order " + order.getId();
                title = new PushHeading(body, order_status_updated, null, null);
                message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);
                break;
            case STAGE_3_READY_FOR_COLLECTION:
                break;
            case STAGE_4_ON_THE_ROAD:
                title = new PushHeading("The driver is on the way", order_status_updated, null, null);
                message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);
                break;
            case STAGE_5_ARRIVED:
                title = new PushHeading("The driver has arrived", order_status_updated, null, null);
                message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);
                break;
            case STAGE_6_WITH_CUSTOMER:
                title = new PushHeading("Your order has been delivered", order_status_updated, null, null);
                message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);
                order.setStage(OrderStage.STAGE_7_ALL_PAID);
                break;
            case STAGE_7_ALL_PAID:
                break;
            case CANCELLED:
                break;
        }

        var customer = userProfileService.find(order.getCustomerId());
        List<Device> customerDevices = deviceService.findByUserId(customer.getId());
        if (!customerDevices.isEmpty()) {
            pushNotificationService.sendNotifications(customerDevices, message);
        }
        try {
            if (originalStage == OrderStage.STAGE_5_ARRIVED) {
                whatsappNotificationService.notifyDriverArrivedForPickup(order, customer);
            } else if (originalStage == OrderStage.STAGE_6_WITH_CUSTOMER) {
                whatsappNotificationService.notifyDriverArrivedForDropOff(order, customer);
            } else if (title != null) {
                whatsappNotificationService.sendMessage(customer.getMobileNumber(), title.getBody());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to send WhatsApp delivery update notification for order {}", order.getId(), e);
        }

    }

    @Override
    public void handleOrderCancelledEvent(OrderCancelledEvent newOrderEvent) {

    }
}
