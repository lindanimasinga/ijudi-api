package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.commons.order.events.OrderCancelledEvent;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;

public interface OrderEventHandler {

    void handleNewOrderEvent(NewOrderEvent newOrderEvent) throws Exception;
    void handleOrderUpdatedEvent(OrderUpdatedEvent orderUpdatedEvent);
    void handleOrderCancelledEvent(OrderCancelledEvent cancelledEvent);
}
