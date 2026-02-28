package io.curiousoft.izinga.messaging.firebase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.GoogleCredentials;
import io.curiousoft.izinga.messaging.whatsapp.ChatSession;
import io.curiousoft.izinga.messaging.whatsapp.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FirestoreService {

    private static final Logger LOG = LoggerFactory.getLogger(FirestoreService.class);

    private static final String FIRESTORE_BASE = "https://firestore.googleapis.com/v1";
    private static final String DATABASE = "izinga";
    private static final String COLLECTION = "chatSessions";

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GoogleCredentials credentials;
    private final String projectId;

    public FirestoreService(FirebaseAuthConfig firebaseAuthConfig, RestTemplate restTemplate) throws Exception {
        this.restTemplate = restTemplate;

        var keyStream = new ByteArrayInputStream(firebaseAuthConfig.configAsJson().getBytes());
        this.credentials = GoogleCredentials.fromStream(keyStream)
                .createScoped(List.of("https://www.googleapis.com/auth/datastore", "https://www.googleapis.com/auth/cloud-platform"));
        this.projectId = firebaseAuthConfig.projectId();
    }

    /**
     * Create a document with auto-generated id under collection 'izinga'. Returns the generated document id.
     */
    public String createDocument(ChatSession data) throws Exception {
        Objects.requireNonNull(data, "data must not be null");
        String url = String.format("%s/projects/%s/databases/%s/documents/%s", FIRESTORE_BASE, projectId, DATABASE, COLLECTION);

        Map<String, Object> body = Map.of("fields", toFirestoreFields(data));
        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Firestore responded with status " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }
            // response contains 'name' like: projects/{project}/databases/(default)/documents/izinga/{docId}
            ObjectNode node = (ObjectNode) mapper.readTree(resp.getBody());
            String name = node.has("name") ? node.get("name").asText() : null;
            if (name == null) return null;
            String[] parts = name.split("/");
            return parts[parts.length - 1];
        } catch (Exception e) {
            LOG.error("Error creating firestore document", e);
            throw e;
        }
    }

    /**
     * Set (create/replace) a document with the provided id under collection 'izinga'.
     */
    public void setDocument(String docId, Map<String, Object> data) throws Exception {
        Objects.requireNonNull(docId, "docId must not be null");
        Objects.requireNonNull(data, "data must not be null");
        String url = String.format("%s/projects/%s/databases/%s/documents/%s/%s", FIRESTORE_BASE, projectId, DATABASE, COLLECTION, docId);

        Map<String, Object> body = Map.of("fields", toFirestoreFields(data));
        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        restTemplate.put(url, request);
    }

    /**
     * Get a document by id and convert Firestore fields to a plain map.
     */
    public Map<String, Object> getDocument(String docId) throws Exception {
        Objects.requireNonNull(docId, "docId must not be null");
        String url = String.format("%s/projects/%s/databases/%s/documents/%s/%s", FIRESTORE_BASE, projectId, DATABASE, COLLECTION, docId);

        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Firestore responded with status " + resp.getStatusCodeValue());
        }
        ObjectNode node = (ObjectNode) mapper.readTree(resp.getBody());
        if (!node.has("fields")) return Collections.emptyMap();
        var fieldsNode = node.get("fields");
        return fromFirestoreFields(mapper.convertValue(fieldsNode, new TypeReference<>() {}));
    }

    /**
     * Find a chat session document by customerId. Returns a ChatSession object, or null if not found.
     */
    public ChatSession findChatSessionByCustomerId(String customerId) throws Exception {
        Objects.requireNonNull(customerId, "customerId must not be null");
        String url = String.format("%s/projects/%s/databases/%s/documents:runQuery", FIRESTORE_BASE, projectId, DATABASE);

        // Build structuredQuery body
        Map<String, Object> fieldPath = Map.of("fieldPath", "customerId");
        Map<String, Object> value = Map.of("stringValue", customerId);
        Map<String, Object> fieldFilter = Map.of(
                "field", fieldPath,
                "op", "EQUAL",
                "value", value
        );
        Map<String, Object> where = Map.of("fieldFilter", fieldFilter);
        Map<String, Object> from = Map.of("collectionId", COLLECTION);
        Map<String, Object> structuredQuery = new HashMap<>();
        structuredQuery.put("from", List.of(from));
        structuredQuery.put("where", where);
        structuredQuery.put("limit", 1);

        Map<String, Object> body = Map.of("structuredQuery", structuredQuery);

        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Firestore responded with status " + resp.getStatusCodeValue());
        }

        var root = mapper.readTree(resp.getBody());
        if (root == null || !root.isArray() || root.isEmpty()) return null;

        for (var node : root) {
            if (node.has("document")) {
                var docNode = node.get("document");
                String name = docNode.has("name") ? docNode.get("name").asText() : null;
                var fieldsNode = docNode.get("fields");
                if (fieldsNode == null) continue;
                Map<String, Object> rawFields = mapper.convertValue(fieldsNode, new TypeReference<>() {});
                Map<String, Object> data = fromFirestoreFields(rawFields);
                ChatSession session = ChatSession.fromMap(data);
                if (name != null) {
                    String[] parts = name.split("/");
                    session.setId(parts[parts.length - 1]);
                }
                return session;
            }
        }

        return null;
    }

    /**
     * Write a message into the chat session messages subcollection for the given customerId.
     * If a chat session does not exist for the customer, create one.
     * Returns the created message document id.
     */
    public String writeMessageForCustomer(String customerId, String customerName, FireStoreTextMessage messageData) throws Exception {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(messageData, "messageData must not be null");

        // 1) find or create chat session
        ChatSession session = findChatSessionByCustomerId(customerId);
        String sessionId;
        if (session == null) {

            Instant lastMsgTs = messageData.getTimestamp() != null ? messageData.getTimestamp() : Instant.now();
            String generatedSessionId = "session_" + System.currentTimeMillis();

            ChatSession newSession = ChatSession.builder()
                    .customerId(customerId)
                    .customerMobileNumber(customerId)
                    .customerName(customerName)
                    .lastMessage(messageData.getMessage())
                    .lastMessageTimestamp(lastMsgTs)
                    .sessionId(generatedSessionId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .status(ChatSession.ChatStatus.ACTIVE)
                    .build();

            sessionId = createDocument(newSession);
            LOG.info("Created new chatSession {} (sessionId={}) for customer {}", sessionId, generatedSessionId, customerId);
        } else {
            sessionId = session.getId();
        }

        // ensure createdAt/timestamp are set
        if (messageData.getCreatedAt() == null) messageData.setCreatedAt(Instant.now());
        if (messageData.getTimestamp() == null) messageData.setTimestamp(Instant.now());

        // Build URL for messages subcollection
        String url = String.format("%s/projects/%s/databases/%s/documents/%s/%s/messages", FIRESTORE_BASE, projectId, DATABASE, COLLECTION, sessionId);
        Map<String, Object> body = Map.of("fields", toFirestoreFields(messageData.toMap()));
        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Firestore responded with status " + resp.getStatusCodeValue() + ": " + resp.getBody());
        }
        ObjectNode node = (ObjectNode) mapper.readTree(resp.getBody());
        String name = node.has("name") ? node.get("name").asText() : null;
        if (name == null) return null;
        String[] parts = name.split("/");
        String messageId = parts[parts.length - 1];

        // Optionally update session meta (e.g., lastMessageAt)
        try {
            Map<String, Object> update = new LinkedHashMap<>();
            // last message text
            if (messageData.getMessage() != null) update.put("lastMessage", messageData.getMessage());
            // last message timestamp
            Instant lmTs = messageData.getTimestamp() != null ? messageData.getTimestamp() : Instant.now();
            update.put("lastMessageTimestamp", lmTs);
            // customer name and mobile (ensure we don't overwrite with null)
            if (messageData.getMeta() != null && messageData.getMeta().get("contactName") != null) {
                update.put("customerName", String.valueOf(messageData.getMeta().get("contactName")));
            }
            update.put("customerMobileNumber", customerId);
            update.put("updatedAt", Instant.now());
            setDocument(sessionId, update);
        } catch (Exception e) {
            LOG.warn("Failed to update chatSession metadata for {}", sessionId, e);
        }

        return messageId;
    }

    /**
     * List message documents under chatSessions/{sessionId}/messages and convert to typed Message objects.
     */
    public List<Message> getMessagesForSession(String sessionId, int limit) throws Exception {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (limit <= 0) limit = 50;
        String url = String.format("%s/projects/%s/databases/%s/documents/%s/%s/messages?pageSize=%d", FIRESTORE_BASE, projectId, DATABASE, COLLECTION, sessionId, limit);

        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Firestore responded with status " + resp.getStatusCodeValue());
        }

        var root = mapper.readTree(resp.getBody());
        List<Message> messages = new ArrayList<>();

        // Response may contain 'documents' array
        if (root.has("documents") && root.get("documents").isArray()) {
            for (var doc : root.get("documents")) {
                var fieldsNode = doc.get("fields");
                if (fieldsNode == null) continue;
                Map<String, Object> rawFields = mapper.convertValue(fieldsNode, new TypeReference<>() {});
                Map<String, Object> data = fromFirestoreFields(rawFields);
                Message msg = Message.fromMap(data);
                String name = doc.has("name") ? doc.get("name").asText() : null;
                if (name != null) {
                    String[] parts = name.split("/");
                    msg.setId(parts[parts.length - 1]);
                }
                messages.add(msg);
            }
        } else if (root.isArray()) {
            // fallback: array of documents
            for (var node : root) {
                if (node.has("document")) {
                    var docNode = node.get("document");
                    var fieldsNode = docNode.get("fields");
                    if (fieldsNode == null) continue;
                    Map<String, Object> rawFields = mapper.convertValue(fieldsNode, new TypeReference<>() {});
                    Map<String, Object> data = fromFirestoreFields(rawFields);
                    Message msg = Message.fromMap(data);
                    String name = docNode.has("name") ? docNode.get("name").asText() : null;
                    if (name != null) {
                        String[] parts = name.split("/");
                        msg.setId(parts[parts.length - 1]);
                    }
                    messages.add(msg);
                }
            }
        }

        return messages;
    }

    /**
     * Get ChatSession by its Firestore document id.
     */
    public ChatSession getChatSessionById(String docId) throws Exception {
        Objects.requireNonNull(docId, "docId must not be null");
        Map<String, Object> data = getDocument(docId);
        if (data == null || data.isEmpty()) return null;
        ChatSession s = ChatSession.fromMap(data);
        s.setId(docId);
        return s;
    }

    /**
     * Get a single Message document from chatSessions/{sessionId}/messages/{messageId}
     */
    public Message getMessageForSession(String sessionId, String messageId) throws Exception {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        String url = String.format("%s/projects/%s/databases/%s/documents/%s/%s/messages/%s", FIRESTORE_BASE, projectId, DATABASE, COLLECTION, sessionId, messageId);

        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Firestore responded with status " + resp.getStatusCodeValue());
        }
        ObjectNode node = (ObjectNode) mapper.readTree(resp.getBody());
        if (!node.has("fields")) return null;
        var fieldsNode = node.get("fields");
        Map<String, Object> rawFields = mapper.convertValue(fieldsNode, new TypeReference<>() {});
        Map<String, Object> data = fromFirestoreFields(rawFields);
        Message msg = Message.fromMap(data);
        msg.setId(messageId);
        return msg;
    }

    // --- helpers to convert between normal JSON and Firestore field-value format ---

    private Map<String, Object> toFirestoreFields(Map<String, Object> data) {
        if (data == null) return Collections.emptyMap();
        return data.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> valueToFirestore(e.getValue())));
    }

    private Map<String, Object> toFirestoreFields(ChatSession data) {
        if (data == null) return Collections.emptyMap();
        return toFirestoreFields(data.toMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> valueToFirestore(Object value) {
        if (value == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nullValue", null);
            return m;
        }
        if (value instanceof String) return Map.of("stringValue", value);
        if (value instanceof Integer || value instanceof Long) return Map.of("integerValue", String.valueOf(value));
        if (value instanceof Float || value instanceof Double) return Map.of("doubleValue", value);
        if (value instanceof Boolean) return Map.of("booleanValue", value);
        if (value instanceof Instant) {
            String ts = DateTimeFormatter.ISO_INSTANT.format((Instant) value);
            return Map.of("timestampValue", ts);
        }
        if (value instanceof Date) {
            String ts = DateTimeFormatter.ISO_INSTANT.format(((Date) value).toInstant());
            return Map.of("timestampValue", ts);
        }
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            return Map.of("mapValue", Map.of("fields", toFirestoreFields(map)));
        }
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<Object> converted = list.stream().map(this::valueToFirestore).collect(Collectors.toList());
            return Map.of("arrayValue", Map.of("values", converted));
        }
        // fallback to string
        return Map.of("stringValue", String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromFirestoreFields(Map<String, Object> firestoreFields) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var entry : firestoreFields.entrySet()) {
            String key = entry.getKey();
            Object valObj = entry.getValue();
            if (!(valObj instanceof Map)) continue;
            Map<String, Object> valueMap = (Map<String, Object>) valObj;
            out.put(key, firestoreValueToJava(valueMap));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object firestoreValueToJava(Map<String, Object> valueMap) {
        if (valueMap.containsKey("stringValue")) return valueMap.get("stringValue");
        if (valueMap.containsKey("integerValue")) {
            String s = String.valueOf(valueMap.get("integerValue"));
            try { return Long.parseLong(s); } catch (Exception e) { return s; }
        }
        if (valueMap.containsKey("doubleValue")) return valueMap.get("doubleValue");
        if (valueMap.containsKey("booleanValue")) return valueMap.get("booleanValue");
        if (valueMap.containsKey("timestampValue")) return valueMap.get("timestampValue").toString();
        if (valueMap.containsKey("mapValue")) {
            Map<String, Object> mapValue = (Map<String, Object>) valueMap.get("mapValue");
            if (mapValue.containsKey("fields")) {
                return fromFirestoreFields((Map<String, Object>) mapValue.get("fields"));
            }
            return mapValue;
        }
        if (valueMap.containsKey("arrayValue")) {
            Map<String, Object> arr = (Map<String, Object>) valueMap.get("arrayValue");
            List<Object> values = (List<Object>) arr.get("values");
            if (values == null) return Collections.emptyList();
            return values.stream().map(v -> {
                if (v instanceof Map) return firestoreValueToJava((Map<String, Object>) v);
                return v;
            }).collect(Collectors.toList());
        }
        if (valueMap.containsKey("nullValue")) return null;
        return null;
    }

    private String getAccessToken() throws Exception {
        synchronized (credentials) {
            credentials.refreshIfExpired();
            var token = credentials.getAccessToken();
            if (token == null) throw new IllegalStateException("Unable to obtain access token for firestore");
            return token.getTokenValue();
        }
    }
}
