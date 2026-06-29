package io.curiousoft.izinga.messaging.aiAgent.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a single message in a conversation history.
 * Stored as a nested document within ConversationHistory in MongoDB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMessage {

    /**
     * Role of the message sender: "user" or "assistant"
     */
    private String role;

    /**
     * The actual message content
     */
    private String content;

    /**
     * Timestamp when this message was created
     */
    private Instant timestamp;
}

