package io.curiousoft.izinga.messaging.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappWebhookPayload {

    private String object;
    private List<Entry> entry;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private String id;
        private List<Change> changes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Change {
        private Value value;
        private String field;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        @JsonProperty("messaging_product")
        private String messagingProduct;

        private Metadata metadata;
        private List<Contact> contacts;
        private List<Message> messages;
        private List<Status> statuses;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Metadata {
            @JsonProperty("display_phone_number")
            private String displayPhoneNumber;

            @JsonProperty("phone_number_id")
            private String phoneNumberId;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Contact {
            private ContactProfile profile;

            @JsonProperty("wa_id")
            private String waId;

            @JsonProperty("identity_key_hash")
            private String identityKeyHash;

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class ContactProfile {
                private String name;
            }
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Message {
            @JsonProperty("from")
            private String from;

            @JsonProperty("id")
            private String id;

            @JsonProperty("timestamp")
            private String timestamp;

            @JsonProperty("type")
            private String type;

            private Text text;
            private Context context;
            private Referral referral;
            private Interactive interactive;
            private Location location; // added to support location messages
            private Image image;       // added to support image messages

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Text {
                private String body;
            }

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Context {
                private String from;
                private String id;
                @JsonProperty("referred_product")
                private ReferredProduct referredProduct;

                @JsonIgnoreProperties(ignoreUnknown = true)
                @Data
                @NoArgsConstructor
                @AllArgsConstructor
                public static class ReferredProduct {
                    @JsonProperty("catalog_id")
                    private String catalogId;

                    @JsonProperty("product_retailer_id")
                    private String productRetailerId;
                }
            }

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Referral {
                @JsonProperty("source_url")
                private String sourceUrl;

                @JsonProperty("source_id")
                private String sourceId;

                @JsonProperty("source_type")
                private String sourceType;

                private String body;
                private String headline;

                @JsonProperty("media_type")
                private String mediaType;

                @JsonProperty("image_url")
                private String imageUrl;

                @JsonProperty("video_url")
                private String videoUrl;

                @JsonProperty("thumbnail_url")
                private String thumbnailUrl;

                @JsonProperty("ctwa_clid")
                private String ctwaClid;

                @JsonProperty("welcome_message")
                private WelcomeMessage welcomeMessage;

                @Data
                @NoArgsConstructor
                @AllArgsConstructor
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class WelcomeMessage {
                    private String text;
                }
            }

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Interactive {
                private String type;
                @JsonProperty("button_reply")
                private ButtonReply buttonReply;
                @JsonProperty("list_reply")
                private ListReply listReply;

                @Data
                @NoArgsConstructor
                @AllArgsConstructor
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class ButtonReply {
                    private String id;
                    private String title;
                }

                @Data
                @NoArgsConstructor
                @AllArgsConstructor
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class ListReply {
                    private String id;
                    private String title;
                }
            }

            // Location message payload
            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Location {
                private String address;
                private Double latitude;
                private Double longitude;
                private String name;
                private String url;
            }

            // Image message payload
            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Image {
                private String caption;

                @JsonProperty("mime_type")
                private String mimeType;

                private String sha256;
                private String id;
                private String url;
            }
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Status {
            @JsonProperty("id")
            private String id;

            @JsonProperty("status")
            private String status;

            @JsonProperty("timestamp")
            private String timestamp;

            @JsonProperty("recipient_id")
            private String recipientId;
        }
    }
}
