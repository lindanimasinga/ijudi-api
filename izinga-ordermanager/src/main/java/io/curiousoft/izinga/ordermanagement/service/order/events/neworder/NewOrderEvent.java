package io.curiousoft.izinga.ordermanagement.service.order.events.neworder;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.StoreProfile;

public class NewOrderEvent extends OrderEvent {

    public NewOrderEvent(Object source, Order newOrder, String messengerId, StoreProfile receivingStore) {
        super(source, newOrder, messengerId, receivingStore);
    }
}
