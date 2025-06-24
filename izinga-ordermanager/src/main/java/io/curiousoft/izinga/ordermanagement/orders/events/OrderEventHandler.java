package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.commons.order.events.OrderCancelledEvent;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public interface OrderEventHandler {

    void handleNewOrderEvent(NewOrderEvent newOrderEvent) throws Exception;

    @Async
    @EventListener
    void handleNewOrderEventToEmail(NewOrderEvent event) throws Exception;

    @Async
    @EventListener
    void handleNewOrderEventToWhatsapp(NewOrderEvent event) throws Exception;

    void handleOrderUpdatedEvent(OrderUpdatedEvent orderUpdatedEvent);
    void handleOrderCancelledEvent(OrderCancelledEvent cancelledEvent);
}
