package io.curiousoft.izinga.messaging.firebase;


import java.util.List;

public class FCMUnSubscribeMessage {

    String to;
    List registration_tokens;

    public FCMUnSubscribeMessage(String to, List registration_tokens) {
        this.to = to;
        this.registration_tokens = registration_tokens;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public List getRegistration_tokens() {
        return registration_tokens;
    }

    public void setRegistration_tokens(List registration_tokens) {
        this.registration_tokens = registration_tokens;
    }
}
