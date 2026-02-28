package io.curiousoft.izinga.messaging.whatsapp;

import org.springframework.context.ApplicationEvent;

public class WhatsappTemplateReplyEvent extends ApplicationEvent {

    private final String from;
    private final String id;
    private final String title;
    private final WhatsappWebhookPayload.Value.Message rawMessage;

    public WhatsappTemplateReplyEvent(Object source, String from, String id, String title, WhatsappWebhookPayload.Value.Message rawMessage) {
        super(source);
        this.from = from;
        this.id = id;
        this.title = title;
        this.rawMessage = rawMessage;
    }

    public String getFrom() {
        return from;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public WhatsappWebhookPayload.Value.Message getRawMessage() {
        return rawMessage;
    }
}
