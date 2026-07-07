package io.curiousoft.izinga.commons.payout.events

import org.springframework.context.ApplicationEvent
import java.math.BigDecimal

class AmbassadorPayoutEvent(
    source: Any,
    val ambassadorId: String,
    val driverId: String,
    val commissionAmount: BigDecimal,
    val payoutId: String
) : ApplicationEvent(source)
