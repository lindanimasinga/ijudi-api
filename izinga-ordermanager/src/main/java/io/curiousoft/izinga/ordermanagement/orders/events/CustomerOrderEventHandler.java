package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.ordermanagement.service.zoomsms.ZoomSmsNotificationService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public record CustomerOrderEventHandler(PushNotificationService pushNotificationService,
                                        EmailNotificationService emailNotificationService,
                                        ZoomSmsNotificationService zoomSmsNotificationService,
                                        DeviceService deviceService,
                                        UserProfileService userProfileService) implements OrderEventHandler {

    @EventListener
    @Override
    public void handleNewOrderEvent(NewOrderEvent newOrderEvent) {
        var store = newOrderEvent.getReceivingStore();
        var order = newOrderEvent.getOrder();
        var customer = userProfileService.find(order.getCustomerId());
        List<Device> shopDevices = deviceService.findByUserId(customer.getId());
        if (!shopDevices.isEmpty()) {
            pushNotificationService.notifyStoreOrderPlaced(store.getName(), shopDevices, order);
        } else {
            zoomSmsNotificationService.notifyShopOrderPlaced(store, order, customer);
        }
    }

    @Override
    public void handleOrderUpdatedEvent(NewOrderEvent newOrderEvent) {

    }

    @Override
    public void handleOrderCancelledEvent(NewOrderEvent newOrderEvent) {

    }
}
