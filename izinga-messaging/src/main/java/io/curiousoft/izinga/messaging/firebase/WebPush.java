package io.curiousoft.izinga.messaging.firebase;

public record WebPush(FcmOptions fcm_options) {

    public record FcmOptions(String link) {

    }
}
