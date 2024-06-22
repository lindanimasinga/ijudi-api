package io.curiousoft.izinga.commons.payout.events;

import org.springframework.context.ApplicationEvent;

public class OrderPayoutEvent extends ApplicationEvent {

    private final String orderId;
    private final boolean storePaid;
    private final boolean messengerPaid;

    public OrderPayoutEvent(Object source, String orderId, boolean storePaid, boolean messengerPaid) {
        super(source);
        this.orderId = orderId;
        this.storePaid = storePaid;
        this.messengerPaid = messengerPaid;
    }

    public String getOrderId() {
        return orderId;
    }

    public boolean isStorePaid() {
        return storePaid;
    }

    public boolean isMessengerPaid() {
        return messengerPaid;
    }
}
