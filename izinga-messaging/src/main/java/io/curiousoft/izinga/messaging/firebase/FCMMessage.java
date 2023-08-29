package io.curiousoft.izinga.messaging.firebase;


public class FCMMessage {

    private Object data;
    private String to;
    private FCMNotification notification;

    public FCMMessage() {
    }

    public FCMMessage(String to, FCMNotification notification, Object data) {
        this.data = data;
        this.to = to;
        this.notification = notification;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public FCMNotification getNotification() {
        return notification;
    }

    public void setNotification(FCMNotification notification) {
        this.notification = notification;
    }
}
