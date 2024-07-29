package io.curiousoft.izinga.commons.order.events

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.order.events.OrderEvent
import org.springframework.context.ApplicationEvent

abstract class OrderEvent(source: Any, val order: Order, val messenger: String?, val receivingStore: StoreProfile?) :
    ApplicationEvent(
        source
    )