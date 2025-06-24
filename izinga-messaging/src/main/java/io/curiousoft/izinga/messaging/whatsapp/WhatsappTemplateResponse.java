package io.curiousoft.izinga.messaging.whatsapp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class WhatsappTemplateResponse {

    private String messaging_product;
    private List<Contact> contacts;
    private List<Message> messages;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class Contact {
        private String input;
        private String wa_id;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class Message {
        private String id; // e.g. "wamid.123..."
    }
}
