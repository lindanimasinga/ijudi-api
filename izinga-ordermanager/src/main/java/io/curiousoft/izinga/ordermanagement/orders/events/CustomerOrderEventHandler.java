package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.ordermanagement.service.zoomsms.ZoomSmsNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public record CustomerOrderEventHandler(EmailNotificationService emailNotificationService,
                                        ZoomSmsNotificationService zoomSmsNotificationService) implements OrderEventHandler {

    @EventListener
    @Override
    public void handleNewOrderEvent(NewOrderEvent newOrderEvent) {

    }

    @Override
    public void handleOrderUpdatedEvent(NewOrderEvent newOrderEvent) {

    }

    @Override
    public void handleOrderCancelledEvent(NewOrderEvent newOrderEvent) {

    }
}
