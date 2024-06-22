package io.curiousoft.izinga.commons.payout.events

import org.springframework.context.ApplicationEvent

class OrderPayoutEvent(source: Any, val orderId: String, val isStorePaid: Boolean, val isMessengerPaid: Boolean) :
    ApplicationEvent(source!!)