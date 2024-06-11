package io.curiousoft.izinga.ordermanagement.orders.events.neworder;


import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.StoreProfile;

public class OrderUpdatedEvent extends OrderEvent {

    public OrderUpdatedEvent(Object source, Order newOrder, String messengerId, StoreProfile receivingStore) {
        super(source, newOrder, messengerId, receivingStore);
    }
}
