package io.curiousoft.izinga.messaging.whatsapp;

import lombok.Data;

import java.util.List;

@Data
public class WhatsappTemplateRequest {

    private String messaging_product = "whatsapp";
    private String to;
    private String type = "template";
    private Template template;

    @Data
    public static class Template {
        private String name; // e.g. order_confirmation
        private Language language;
        private List<Component> components;
    }

    @Data
    public static class Language {
        private String code = "en_US"; // default or based on user
    }

    @Data
    public static class Component {
        private ComponentType type;               // Required
        private ButtonSubType sub_type;           // Required if type == BUTTON
        private Integer index;                    // Required if type == BUTTON
        private List<Parameter> parameters;
    }

    public enum ComponentType {
        HEADER,
        BODY,
        BUTTON
    }

    public enum ButtonSubType {
        URL,
        QUICK_REPLY
    }
    
    @Data
    public static class Parameter {
        private ParameterType type;               // Required
        private String text;                      // Required if type == TEXT
        private Currency currency;                // Required if type == CURRENCY
        private DateTime date_time;               // Required if type == DATE_TIME
        private MediaObject image;                // Required if type == IMAGE
        private MediaObject document;             // Required if type == DOCUMENT
        private MediaObject video;                // Required if type == VIDEO
    }

    public enum ParameterType {
        TEXT,
        CURRENCY,
        DATE_TIME,
        DOCUMENT,
        IMAGE,
        VIDEO
    }

    @Data
    public static class Currency {
        private String fallback_value;            // Required
        private String code;                      // ISO 4217
        private long amount_1000;                 // Required
    }

    @Data
    public static class DateTime {
        private String fallback_value;            // Required
    }

    @Data
    public static class MediaObject {
        private String id;                        // Optional
        private String link;                      // Optional
    }

    @Data
    public static class TextMessage {
        private String body;                      // Required
        private boolean preview_url;              // Optional
    }

    @Data
    public static class Reaction {
        private String message_id;                // Required
        private String emoji;                     // Required
    }
}
