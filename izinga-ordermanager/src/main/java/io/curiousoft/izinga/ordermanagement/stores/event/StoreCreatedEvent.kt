package io.curiousoft.izinga.ordermanagement.stores.event

import io.curiousoft.izinga.commons.model.StoreProfile
import org.springframework.context.ApplicationEvent

abstract class StoreCreatedEvent(source: Any, val storeProfile: StoreProfile) :
    ApplicationEvent(
        source
    )