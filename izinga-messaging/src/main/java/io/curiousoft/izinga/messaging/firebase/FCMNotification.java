package io.curiousoft.izinga.messaging.firebase;

public class FCMNotification {

    String body;
    String title;
    String icon;

    public FCMNotification() {
    }

    public FCMNotification(String body, String title, String icon) {
        this.body = body;
        this.title = title;
        this.icon = icon;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}
