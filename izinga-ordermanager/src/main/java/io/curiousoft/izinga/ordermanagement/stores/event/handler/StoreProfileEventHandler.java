package io.curiousoft.izinga.ordermanagement.stores.event.handler;

import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.commons.order.events.OrderCancelledEvent;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.ordermanagement.stores.event.StoreCreatedEvent;
import io.curiousoft.izinga.ordermanagement.stores.event.StoreDeletedEvent;
import io.curiousoft.izinga.ordermanagement.stores.event.StoreUpdatedEvent;
import org.springframework.context.event.EventListener;

public interface StoreProfileEventHandler {

    @EventListener
    void handleNewStoreCreatedEvent(StoreCreatedEvent event) throws Exception;

    void handleStoreUpdatedEvent(StoreUpdatedEvent event);

    void handleStoreDeletedEvent(StoreDeletedEvent event);
}
