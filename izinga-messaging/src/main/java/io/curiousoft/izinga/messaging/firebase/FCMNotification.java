package io.curiousoft.izinga.messaging.firebase;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FCMNotification that = (FCMNotification) o;
        return Objects.equals(body, that.body)
                && Objects.equals(title, that.title)
                && Objects.equals(icon, that.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(body, title, icon);
    }
}
