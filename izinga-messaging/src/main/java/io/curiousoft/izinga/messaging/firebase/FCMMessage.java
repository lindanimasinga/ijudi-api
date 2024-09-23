package io.curiousoft.izinga.messaging.firebase;


public class FCMMessage {

    private MessageContainer message;

    public FCMMessage(String token, FCMNotification notification) {
        this.message = new MessageContainer(token, notification);
    }

    public MessageContainer getMessage() {
        return message;
    }

    public record MessageContainer(String token, FCMNotification notification) {
    }
}
