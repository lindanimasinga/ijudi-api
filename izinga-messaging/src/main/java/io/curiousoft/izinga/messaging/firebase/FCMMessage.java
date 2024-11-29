package io.curiousoft.izinga.messaging.firebase;


public class FCMMessage {

    private MessageContainer message;

    public FCMMessage(String token, FCMNotification notification, WebPush webPush) {
        this.message = new MessageContainer(token, notification, webPush);
    }

    public MessageContainer getMessage() {
        return message;
    }

    public record MessageContainer(String token, FCMNotification notification, WebPush webpush) {
    }
}
