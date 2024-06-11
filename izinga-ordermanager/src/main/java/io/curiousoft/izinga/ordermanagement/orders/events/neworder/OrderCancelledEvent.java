package io.curiousoft.izinga.ordermanagement.orders.events.neworder;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.StoreProfile;

public class OrderCancelledEvent extends OrderEvent {

    public OrderCancelledEvent(Object source, Order newOrder, String messengerId, StoreProfile receivingStore) {
        super(source, newOrder, messengerId, receivingStore);
    }
}
