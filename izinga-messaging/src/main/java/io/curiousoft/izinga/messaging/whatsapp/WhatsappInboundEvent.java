package io.curiousoft.izinga.messaging.whatsapp;

import org.springframework.context.ApplicationEvent;

public class WhatsappInboundEvent extends ApplicationEvent {

    private final WhatsappWebhookPayload payload;

    public WhatsappInboundEvent(Object source, WhatsappWebhookPayload payload) {
        super(source);
        this.payload = payload;
    }

    public WhatsappWebhookPayload getPayload() {
        return payload;
    }
}
