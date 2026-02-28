package io.curiousoft.izinga.messaging.whatsapp;

import lombok.Data;

import java.util.Map;

@Data
public class WhatsappTextRequest {
    private String messaging_product = "whatsapp";
    private String to;
    private String type = "text";
    private Text text;

    @Data
    public static class Text {
        private String body;
    }
}

