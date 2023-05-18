package io.curiousoft.izinga.messaging.domain;

public class EmailAttachment {

    String mimeType;
    byte[] data;
    String name;

    public EmailAttachment(String mimeType, byte[] data, String name) {
        this.mimeType = mimeType;
        this.data = data;
        this.name = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
