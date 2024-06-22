package io.curiousoft.izinga.commons.order.events

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.StoreProfile

class NewOrderEvent(source: Any, newOrder: Order, messengerId: String, receivingStore: StoreProfile) :
    OrderEvent(source, newOrder, messengerId, receivingStore)