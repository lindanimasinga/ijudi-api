package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.order.events.QuoteAcceptedEvent;
import io.curiousoft.izinga.ordermanagement.service.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class QuoteAcceptedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteAcceptedEventHandler.class);

    private final WhatsappNotificationService whatsappNotificationService;
    private final UserProfileService userProfileService;

    public QuoteAcceptedEventHandler(WhatsappNotificationService whatsappNotificationService,
                                     UserProfileService userProfileService) {
        this.whatsappNotificationService = whatsappNotificationService;
        this.userProfileService = userProfileService;
    }

    @Async
    @EventListener
    public void handleQuoteAcceptedEvent(QuoteAcceptedEvent event) {
        try {
            Order order = event.getOrder();
            var customer = userProfileService.find(order.getCustomerId());
            if (customer == null) {
                LOG.warn("Customer {} not found for order {}", order.getCustomerId(), order.getId());
                return;
            }
            whatsappNotificationService.notifyQuoteAcceptedToCustomer(order, customer);
            LOG.info("Sent quote accepted WhatsApp message to customer {} for order {}", customer.getMobileNumber(), order.getId());
        } catch (Exception e) {
            LOG.error("Error handling QuoteAcceptedEvent for order {}", event.getOrder().getId(), e);
        }
    }
}

