package io.curiousoft.ijudi.ordermanagement.model;

import java.util.Objects;

public class PushHeading {

    String body;
    String title;
    String icon;

    public PushHeading() {
    }

    public PushHeading(String body, String title, String icon) {
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
        return o instanceof PushHeading && Objects.equals(body, ((PushHeading) o).getBody()) && Objects.equals(title,
                ((PushHeading) o).getTitle()) && Objects.equals(icon, ((PushHeading) o).getIcon());
    }
}
