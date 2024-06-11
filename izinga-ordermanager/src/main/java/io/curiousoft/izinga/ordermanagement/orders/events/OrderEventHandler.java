package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.ordermanagement.orders.events.neworder.NewOrderEvent;

public interface OrderEventHandler {

    void handleNewOrderEvent(NewOrderEvent newOrderEvent) throws Exception;
    void handleOrderUpdatedEvent(NewOrderEvent newOrderEvent);
    void handleOrderCancelledEvent(NewOrderEvent newOrderEvent);
}
