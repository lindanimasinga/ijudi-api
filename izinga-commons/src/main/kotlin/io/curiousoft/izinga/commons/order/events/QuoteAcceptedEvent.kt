package io.curiousoft.izinga.commons.order.events

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.StoreProfile

class QuoteAcceptedEvent(source: Any, order: Order, messengerId: String?, receivingStore: StoreProfile?) :
    OrderEvent(source, order, messengerId, receivingStore)

