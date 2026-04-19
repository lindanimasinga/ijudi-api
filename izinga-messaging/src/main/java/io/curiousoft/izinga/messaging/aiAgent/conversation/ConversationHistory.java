package io.curiousoft.izinga.messaging.aiAgent.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores conversation history for a driver in MongoDB.
 * Each driver has one conversation document keyed by phone number.
 * Messages are stored as an ordered list within the document.
 */
@Document(collection = "ai_conversation_histories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationHistory {

    /**
     * MongoDB document ID (auto-generated)
     */
    @Id
    private String id;

    /**
     * Driver's WhatsApp phone number (e.g., +27812345678)
     */
    @Indexed(unique = true)
    private String driverPhoneNumber;

    /**
     * Driver's name (for context in responses)
     */
    private String driverName;

    /**
     * Ordered list of messages (user and assistant)
     */
    private List<ConversationMessage> messages = new ArrayList<>();

    /**
     * When this conversation was first created
     */
    private Instant createdAt;

    /**
     * When the last message was added
     */
    private Instant lastMessageAt;

    /**
     * When the conversation was last accessed (for cleanup)
     */
    private Instant lastAccessedAt;

    /**
     * Soft delete flag (true = archived/deleted)
     */
    private Boolean archived = false;

    /**
     * Add a user message to the conversation
     */
    public void addUserMessage(String content) {
        this.messages.add(ConversationMessage.builder()
            .role("user")
            .content(content)
            .timestamp(Instant.now())
            .build());
        this.lastMessageAt = Instant.now();
        this.lastAccessedAt = Instant.now();
    }

    /**
     * Add an assistant (AI) message to the conversation
     */
    public void addAssistantMessage(String content) {
        this.messages.add(ConversationMessage.builder()
            .role("assistant")
            .content(content)
            .timestamp(Instant.now())
            .build());
        this.lastMessageAt = Instant.now();
        this.lastAccessedAt = Instant.now();
    }

    /**
     * Get the last N messages (for context in API calls)
     */
    public List<ConversationMessage> getLastNMessages(int n) {
        int size = this.messages.size();
        if (size <= n) {
            return new ArrayList<>(this.messages);
        }
        return new ArrayList<>(this.messages.subList(size - n, size));
    }
}

