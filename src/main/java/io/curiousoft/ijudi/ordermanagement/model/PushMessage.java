package io.curiousoft.ijudi.ordermanagement.model;

import java.util.Objects;

public class PushMessage {

    private PushMessageType pushMessageType;
    private PushHeading pushHeading;
    private Object pushContent;

    public PushMessage(PushMessageType pushMessageType, PushHeading pushHeading, Object pushContent) {
        this.pushMessageType = pushMessageType;
        this.pushHeading = pushHeading;
        this.pushContent = pushContent;
    }

    public PushMessageType getPushMessageType() {
        return pushMessageType;
    }

    public void setPushMessageType(PushMessageType pushMessageType) {
        this.pushMessageType = pushMessageType;
    }

    public PushHeading getPushHeading() {
        return pushHeading;
    }

    public void setPushHeading(PushHeading pushHeading) {
        this.pushHeading = pushHeading;
    }

    public Object getPushContent() {
        return pushContent;
    }

    public void setPushContent(Object pushContent) {
        this.pushContent = pushContent;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PushMessage && Objects.equals(pushMessageType,
                ((PushMessage) obj).getPushMessageType()) && Objects.equals(pushHeading,
                ((PushMessage) obj).getPushHeading()) && Objects.equals(pushContent,
                ((PushMessage) obj).getPushContent());
    }
}
