package io.curiousoft.izinga.messaging.firebase;

import java.util.Objects;

public class FCMMessage {

    private MessageContainer message;

    public FCMMessage(String token, FCMNotification notification, WebPush webPush) {
        this.message = new MessageContainer(token, notification, webPush);
    }

    public MessageContainer getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FCMMessage that = (FCMMessage) o;
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }

    public record MessageContainer(String token, FCMNotification notification, WebPush webpush) {
    }
}
