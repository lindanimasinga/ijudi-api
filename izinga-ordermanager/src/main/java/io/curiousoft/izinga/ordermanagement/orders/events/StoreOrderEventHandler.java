package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.model.OrderStage;
import io.curiousoft.izinga.commons.order.events.OrderCancelledEvent;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public record StoreOrderEventHandler(PushNotificationService pushNotificationService,
                                     AdminOnlyNotificationService adminOnlyNotificationService,
                                     EmailNotificationService emailNotificationService,
                                     DeviceService deviceService,
                                     UserProfileService userProfileService,
                                     ReconService reconService) implements OrderEventHandler {

    @Override
    @EventListener
    public void handleNewOrderEvent(NewOrderEvent event) throws Exception {
        var store = event.getReceivingStore();
        var order = event.getOrder();

        List<Device> shopDevices = deviceService.findByUserId(store.getOwnerId());
        if (!shopDevices.isEmpty()) {
            pushNotificationService.notifyStoreOrderPlaced(store.getName(), shopDevices, order);
        } else {
            adminOnlyNotificationService.notifyShopOrderPlaced(store, order, userProfileService.find(order.getCustomerId()));
        }

        if (StringUtils.hasText(store.getEmailAddress())) {
            emailNotificationService.notifyShops(order, List.of(store.getEmailAddress()));
        }
    }

    @EventListener
    @Override
    public void handleOrderUpdatedEvent(OrderUpdatedEvent event) {
        var order = event.getOrder();
        var store = event.getReceivingStore();

        if (order.getStage() == OrderStage.STAGE_7_ALL_PAID) {
            reconService.generatePayoutForShopAndOrder(order);
        }
    }

    @Override
    public void handleOrderCancelledEvent(OrderCancelledEvent newOrderEvent) {

    }
}
