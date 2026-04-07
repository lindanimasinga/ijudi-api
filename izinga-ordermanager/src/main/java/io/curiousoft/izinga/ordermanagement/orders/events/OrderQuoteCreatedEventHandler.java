package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.events.OrderQuoteCreatedEvent;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.ordermanagement.service.messenger.MessengerLookUpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderQuoteCreatedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OrderQuoteCreatedEventHandler.class);
    private static final double SEARCH_RADIUS_KM = 10.0; // Search within 10km radius
    UserProfileRepo userProfileRepo;
    WhatsappNotificationService whatsappNotificationService;
    private final MessengerLookUpService messengerLookUpService;

    OrderQuoteCreatedEventHandler(
            UserProfileRepo userProfileRepo,
            WhatsappNotificationService whatsappNotificationService,
            io.curiousoft.izinga.ordermanagement.service.messenger.MessengerLookUpService messengerLookUpService) {
        this.userProfileRepo = userProfileRepo;
        this.whatsappNotificationService = whatsappNotificationService;
        this.messengerLookUpService = messengerLookUpService;
    }

    @Async
    @EventListener
    public void handleOrderQuoteCreatedEvent(OrderQuoteCreatedEvent event) {
        try {
            Order order = event.getNewOrder();
            StoreProfile store = event.getReceivingStore();

            if (store == null) {
                LOG.warn("Store not found for order quote {}", order.getId());
                return;
            }

            // Find messengers near the store location
            var lat = order.getShippingData().getShippingDataGeoData().getFromGeoPoint().getLatitude();
            var lng = order.getShippingData().getShippingDataGeoData().getFromGeoPoint().getLongitude();
            List<UserProfile> nearbyMessengers = messengerLookUpService.findNearbyMessengers(lat, lng, SEARCH_RADIUS_KM);
            if(nearbyMessengers.isEmpty()) {
                LOG.info("No nearby messengers found for order quote {} at lat {}, long {}", order.getId(), lat, lng);
                LOG.info("Considering messengers from the drop off Location");
                lat = order.getShippingData().getShippingDataGeoData().getToGeoPoint().getLatitude();
                lng = order.getShippingData().getShippingDataGeoData().getToGeoPoint().getLongitude();
                nearbyMessengers = messengerLookUpService.findNearbyMessengers(lat, lng, SEARCH_RADIUS_KM);
            }

            LOG.info("Found {} nearby messengers for order quote {} lat {}, long {} radius {}km",
                    nearbyMessengers.size(), order.getId(), lat, lng, SEARCH_RADIUS_KM);

            // Send WhatsApp notification to each messenger
            for (UserProfile messenger : nearbyMessengers) {
                try {
                    whatsappNotificationService.notifyMessengerQuoteAvailable(order, store, messenger);
                    LOG.info("Sent quote notification to messenger {}", messenger.getId());
                } catch (Exception e) {
                    LOG.error("Failed to send quote notification to messenger {}", messenger.getId(), e);
                }
            }
        } catch (Exception e) {
            LOG.error("Error handling OrderQuoteCreatedEvent for order {}", event.getNewOrder().getId(), e);
        }
    }
}
