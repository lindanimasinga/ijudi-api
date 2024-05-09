package io.curiousoft.izinga.ordermanagement.service.order.events;

import io.curiousoft.izinga.ordermanagement.service.order.events.neworder.NewOrderEvent;
import org.springframework.context.event.EventListener;

public interface OrderEventHandler {

    void handleNewOrderEvent(NewOrderEvent newOrderEvent) throws Exception;
    void handleOrderUpdatedEvent(NewOrderEvent newOrderEvent);
    void handleOrderCancelledEvent(NewOrderEvent newOrderEvent);
}
