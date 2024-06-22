package io.curiousoft.izinga.commons.order.events;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.StoreProfile;
import org.springframework.context.ApplicationEvent;

public abstract class OrderEvent extends ApplicationEvent {

    private final Order order;
    private final String messengerId;
    private final StoreProfile receivingStore;

    public OrderEvent(Object source, Order newOrder, String messengerId, StoreProfile receivingStore) {
        super(source);
        this.order = newOrder;
        this.messengerId = messengerId;
        this.receivingStore = receivingStore;
    }

    public Order getOrder() {
        return order;
    }

    public String getMessenger() {
        return messengerId;
    }

    public StoreProfile getReceivingStore() {
        return receivingStore;
    }
}
