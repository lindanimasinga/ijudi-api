package io.curiousoft.izinga.messaging.whatsapp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model representing a chat session document stored in the chatSessions collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    @JsonIgnore
    private String id; // Firestore document id
    private String sessionId; // application-level session id (e.g., "session_1772039943430")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    private String customerId;
    private String customerMobileNumber;
    private String customerName;
    private String lastMessage;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastMessageTimestamp;
    private ChatStatus status;
    private String storeId;
    private String storeName;
    private Long unreadMessagesCount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
    // child messages (optional, loaded separately)
    private java.util.List<io.curiousoft.izinga.messaging.whatsapp.Message> messages;
    // Additional metadata if needed
    private Map<String, Object> meta;

    public enum ChatStatus {
        ACTIVE, INACTIVE, UNKNOWN
    }

    /**
     * Convert fields map (as returned by FirestoreService.fromFirestoreFields) into ChatSession
     */
    @SuppressWarnings("unchecked")
    public static ChatSession fromMap(Map<String, Object> map) {
        if (map == null) return null;
        ChatSession s = new ChatSession();
        Object v;

        v = map.get("sessionId");
        if (v != null) s.setSessionId(String.valueOf(v));

        v = map.get("createdAt");
        if (v instanceof Instant) s.setCreatedAt((Instant) v);
        else if (v instanceof String) s.setCreatedAt(Instant.parse((String) v));

        v = map.get("customerId");
        if (v != null) s.setCustomerId(String.valueOf(v));

        v = map.get("customerMobileNumber");
        if (v != null) s.setCustomerMobileNumber(String.valueOf(v));

        v = map.get("customerName");
        if (v != null) s.setCustomerName(String.valueOf(v));

        v = map.get("lastMessage");
        if (v != null) s.setLastMessage(String.valueOf(v));

        v = map.get("lastMessageTimestamp");
        if (v instanceof Instant) s.setLastMessageTimestamp((Instant) v);
        else if (v instanceof String) s.setLastMessageTimestamp(Instant.parse((String) v));

        v = map.get("status");
        if (v != null) {
            try { s.setStatus(ChatStatus.valueOf(String.valueOf(v))); } catch (Exception e) { s.setStatus(ChatStatus.UNKNOWN); }
        }

        v = map.get("storeId");
        if (v != null) s.setStoreId(String.valueOf(v));

        v = map.get("storeName");
        if (v != null) s.setStoreName(String.valueOf(v));

        v = map.get("unreadMessagesCount");
        if (v instanceof Number) s.setUnreadMessagesCount(((Number) v).longValue());
        else if (v != null) {
            try { s.setUnreadMessagesCount(Long.parseLong(String.valueOf(v))); } catch (Exception ignored) {}
        }

        v = map.get("updatedAt");
        if (v instanceof Instant) s.setUpdatedAt((Instant) v);
        else if (v instanceof String) s.setUpdatedAt(Instant.parse((String) v));

        v = map.get("meta");
        if (v instanceof Map) s.setMeta(new LinkedHashMap<>((Map<String, Object>) v));

        return s;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (sessionId != null) m.put("sessionId", sessionId);
        if (createdAt != null) m.put("createdAt", createdAt);
        if (customerId != null) m.put("customerId", customerId);
        if (customerMobileNumber != null) m.put("customerMobileNumber", customerMobileNumber);
        if (customerName != null) m.put("customerName", customerName);
        if (lastMessage != null) m.put("lastMessage", lastMessage);
        if (lastMessageTimestamp != null) m.put("lastMessageTimestamp", lastMessageTimestamp);
        if (status != null) m.put("status", status.name());
        if (storeId != null) m.put("storeId", storeId);
        if (storeName != null) m.put("storeName", storeName);
        if (unreadMessagesCount != null) m.put("unreadMessagesCount", unreadMessagesCount);
        if (updatedAt != null) m.put("updatedAt", updatedAt);
        if (meta != null && !meta.isEmpty()) m.put("meta", meta);
        return m;
    }
}
