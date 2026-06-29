package io.curiousoft.izinga.commons.payout.events

import io.curiousoft.izinga.commons.model.DeviceType
import org.springframework.context.ApplicationEvent
import java.math.BigDecimal

data class PayoutBalanceUpdatedEvent(val userId: String, val balance: BigDecimal, val deviceType: DeviceType, val origin: Any) : ApplicationEvent(origin)