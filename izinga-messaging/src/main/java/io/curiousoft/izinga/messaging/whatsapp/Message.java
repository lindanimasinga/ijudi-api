package io.curiousoft.izinga.messaging.whatsapp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model representing a chat message document stored under chatSessions/{sessionId}/messages
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    // Firestore document id (optional, not stored inside fields)
    @JsonIgnore
    private String id;
    // When the message document was created
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    private Boolean isRead;
    private String message;
    private MessageType messageType;
    private String senderId;
    private SenderType senderType;

    // Message timestamp (semantic timestamp from sender)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    // Additional arbitrary metadata (optional)
    private Map<String, Object> meta;

    public enum MessageType {
        TEXT, IMAGE, LOCATION, SYSTEM, UNKNOWN
    }

    public enum SenderType {
        CUSTOMER, STORE, MESSENGER, SYSTEM
    }

    @JsonIgnore
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (createdAt != null) m.put("createdAt", createdAt);
        if (isRead != null) m.put("isRead", isRead);
        if (message != null) m.put("message", message);
        if (messageType != null) m.put("messageType", messageType.name());
        if (senderId != null) m.put("senderId", senderId);
        if (senderType != null) m.put("senderType", senderType.name());
        if (timestamp != null) m.put("timestamp", timestamp);
        if (meta != null && !meta.isEmpty()) m.put("meta", meta);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Message fromMap(Map<String, Object> map) {
        if (map == null) return null;
        Message msg = new Message();
        Object v;
        v = map.get("createdAt");
        if (v instanceof Instant) msg.setCreatedAt((Instant) v);
        else if (v instanceof String) msg.setCreatedAt(Instant.parse((String) v));
        v = map.get("isRead");
        if (v instanceof Boolean) msg.setIsRead((Boolean) v);
        v = map.get("message");
        if (v != null) msg.setMessage(String.valueOf(v));
        v = map.get("messageType");
        if (v != null) {
            try { msg.setMessageType(MessageType.valueOf(String.valueOf(v))); } catch (Exception e) { msg.setMessageType(MessageType.UNKNOWN); }
        }
        v = map.get("senderId");
        if (v != null) msg.setSenderId(String.valueOf(v));
        v = map.get("senderType");
        if (v != null) {
            try { msg.setSenderType(SenderType.valueOf(String.valueOf(v))); } catch (Exception e) { msg.setSenderType(SenderType.SYSTEM); }
        }
        v = map.get("timestamp");
        if (v instanceof Instant) msg.setTimestamp((Instant) v);
        else if (v instanceof String) msg.setTimestamp(Instant.parse((String) v));
        v = map.get("meta");
        if (v instanceof Map) msg.setMeta((Map<String, Object>) v);
        return msg;
    }
}

