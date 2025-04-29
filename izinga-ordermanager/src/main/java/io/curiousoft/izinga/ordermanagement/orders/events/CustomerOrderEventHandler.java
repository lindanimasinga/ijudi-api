package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.events.OrderCancelledEvent;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.ordermanagement.service.zoomsms.ZoomSmsNotificationService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public record CustomerOrderEventHandler(PushNotificationService pushNotificationService,
                                        EmailNotificationService emailNotificationService,
                                        ZoomSmsNotificationService zoomSmsNotificationService,
                                        DeviceService deviceService,
                                        UserProfileService userProfileService) implements OrderEventHandler {

    @Async
    @EventListener
    @Override
    public void handleNewOrderEvent(NewOrderEvent newOrderEvent) {
        var store = newOrderEvent.getReceivingStore();
        var order = newOrderEvent.getOrder();
        var customer = userProfileService.find(order.getCustomerId());
        List<Device> customerDevices = deviceService.findByUserId(customer.getId());
        if (!customerDevices.isEmpty()) {
            pushNotificationService.notifyStoreOrderPlaced(store.getName(), customerDevices, order);
        } else {
            zoomSmsNotificationService.notifyShopOrderPlaced(store, order, customer);
        }
    }

    @Override
    public void handleOrderUpdatedEvent(OrderUpdatedEvent orderUpdatedEvent) {
        var order = orderUpdatedEvent.getOrder();

        final String order_status_updated = "Order Status Updated";
        PushHeading title = null;
        PushMessage message = null;
        switch (order.getStage()) {
            case STAGE_0_CUSTOMER_NOT_PAID, STAGE_1_WAITING_STORE_CONFIRM:
                return;
            case STAGE_2_STORE_PROCESSING:
                //notify only customer
                title = new PushHeading("The store has started processing your order " + order.getId(), order_status_updated, null, null);
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
        } else {
            //zoomSmsNotificationService.sendMessage(store, order, customer);
        }
    }

    @Override
    public void handleOrderCancelledEvent(OrderCancelledEvent newOrderEvent) {

    }
}
