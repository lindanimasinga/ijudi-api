package io.curiousoft.izinga.messaging.whatsapp.templates;

import lombok.Data;

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

