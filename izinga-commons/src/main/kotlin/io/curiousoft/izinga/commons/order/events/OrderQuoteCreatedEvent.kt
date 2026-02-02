package io.curiousoft.izinga.commons.order.events

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.StoreProfile

class OrderQuoteCreatedEvent(val source: Any, val newOrder: Order, val receivingStore: StoreProfile?)