package io.curiousoft.izinga.ordermanagement.stores.event.handler;

import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.model.PushHeading;
import io.curiousoft.izinga.commons.model.PushMessage;
import io.curiousoft.izinga.commons.model.PushMessageType;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.ordermanagement.stores.event.StoreCreatedEvent;
import io.curiousoft.izinga.ordermanagement.stores.event.StoreDeletedEvent;
import io.curiousoft.izinga.ordermanagement.stores.event.StoreUpdatedEvent;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;


public record StoreAdminStoreProfileEventHandler(PushNotificationService pushNotificationService,
                                                 AdminOnlyNotificationService adminOnlyNotificationService,
                                                 EmailNotificationService emailNotificationService,
                                                 DeviceService deviceService,
                                                 UserProfileService userProfileService,
                                                 @Value("${admin.cellNumber}") List<String> adminCellNumbers) implements StoreProfileEventHandler {

    @EventListener
    @Override
    public void handleNewStoreCreatedEvent(StoreCreatedEvent event) throws Exception {
        var store = event.getStoreProfile();
        adminCellNumbers.stream()
                .map(userProfileService::findUserByPhone)
                .forEach(admin -> {
                    List<Device> shopDevices = deviceService.findByUserId(store.getOwnerId());
                    if (!shopDevices.isEmpty()) {
                        PushHeading heading = new PushHeading("%s Requires approval".formatted(store.getName()),
                                "New store added onto iZinga",
                                null, null);
                        PushMessage pushMessage = new PushMessage(PushMessageType.MARKETING, heading, null);
                        pushNotificationService.sendNotifications(shopDevices, pushMessage);
                    } else {
                        adminOnlyNotificationService.sendMessage(admin.getMobileNumber(),
                                "%s store added onto iZinga requires approval".formatted(store.getName()));
                    }
                });
    }


    @EventListener
    @Override
    public void handleStoreUpdatedEvent(StoreUpdatedEvent event) {

    }

    @EventListener
    @Override
    public void handleStoreDeletedEvent(StoreDeletedEvent event) {

    }
}
