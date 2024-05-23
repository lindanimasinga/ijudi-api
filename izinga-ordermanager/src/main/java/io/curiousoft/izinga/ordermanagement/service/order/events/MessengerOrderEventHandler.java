package io.curiousoft.izinga.ordermanagement.service.order.events;

import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.model.ShippingData;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.ordermanagement.service.order.events.neworder.NewOrderEvent;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public record MessengerOrderEventHandler(PushNotificationService pushNotificationService,
                                         AdminOnlyNotificationService smsNotificationService,
                                         EmailNotificationService emailNotificationService,
                                         DeviceService deviceService,
                                         UserProfileService userProfileService) implements OrderEventHandler {

    @EventListener
    @Override
    public void handleNewOrderEvent(NewOrderEvent event) throws Exception {
        var order = event.getOrder();
        var store = event.getReceivingStore();

        boolean isDelivery = order.getShippingData() != null
                && order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY
                && (store.getStoreType() != StoreType.TIPS || store.getStoreType() != StoreType.CAR_WASH);

        // notify messenger
        if (isDelivery) {
            List<Device> messengerDevices = deviceService.findByUserId(order.getShippingData().getMessengerId());
            pushNotificationService.notifyMessengerOrderPlaced(messengerDevices, order, store);
        }

        if (store.getStoreType() == StoreType.TIPS) {
            var mobileNumber = userProfileService.find(order.getShippingData().getMessengerId()).getMobileNumber();
            var tip = BigDecimal.valueOf(order.getTip()).setScale(2, RoundingMode.HALF_UP);
            var tipReceivedMessage =  String.format("You have received a tip of R%s. Thank you for your service.%niZinga.", tip);
            smsNotificationService.sendMessage(mobileNumber, tipReceivedMessage);
        }
    }

    @Override
    public void handleOrderUpdatedEvent(NewOrderEvent newOrderEvent) {

    }

    @Override
    public void handleOrderCancelledEvent(NewOrderEvent newOrderEvent) {

    }
}