package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.events.OrderQuoteCreatedEvent;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.service.whatsapp.WhatsappNotificationService;
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

    OrderQuoteCreatedEventHandler(
            UserProfileRepo userProfileRepo,
            WhatsappNotificationService whatsappNotificationService) {
        this.userProfileRepo = userProfileRepo;
        this.whatsappNotificationService = whatsappNotificationService;
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
            List<UserProfile> nearbyMessengers = findNearbyMessengers(
                    order.getShippingData().getShippingDataGeoData().getFromGeoPoint().getLatitude(),
                    order.getShippingData().getShippingDataGeoData().getFromGeoPoint().getLongitude() ,
                    SEARCH_RADIUS_KM
            );

            LOG.info("Found {} nearby messengers for order quote {}", nearbyMessengers.size(), order.getId());

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

    private List<UserProfile> findNearbyMessengers(double latitude, double longitude, double radiusKm) {
        // Calculate latitude/longitude bounds for the search radius
        // Approximate: 1 degree latitude ≈ 111km
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)));

        double minLat = latitude - latDelta;
        double maxLat = latitude + latDelta;
        double minLng = longitude - lngDelta;
        double maxLng = longitude + lngDelta;

        // Find messengers within the bounding box
        List<UserProfile> messengers = userProfileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(
                ProfileRoles.MESSENGER,
                minLat,
                maxLat,
                minLng,
                maxLng)
                .stream()
                .filter(it-> Boolean.TRUE.equals(it.getTermsAccepted())
                        && it.getAvailabilityStatus() == ProfileAvailabilityStatus.ONLINE)
                .toList();

        return messengers != null ? messengers : List.of();
    }
}

