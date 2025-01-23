package io.curiousoft.izinga.commons.payout.events

import org.springframework.context.ApplicationEvent

class OrderPayoutEvent(source: Any, val orderId: String, val isStorePaid: Boolean = false, val isMessengerPaid: Boolean = false) :
    ApplicationEvent(source!!)