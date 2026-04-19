package io.curiousoft.izinga.messaging.aiAgent.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for storing AI agent system prompts.
 * Allows dynamic configuration of agent behavior without code changes.
 */
@Document(collection = "ai_agent_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAgentConfig {

    /**
     * MongoDB document ID
     */
    @Id
    private String id;

    /**
     * Name of the agent (e.g., "driver_support", "customer_support")
     */
    private String agentName;

    /**
     * The system prompt that defines agent behavior
     */
    private String systemPrompt;

    /**
     * Optional description of this agent's purpose
     */
    private String description;

    /**
     * Whether this agent config is active
     */
    private Boolean active = true;

    /**
     * When this config was created
     */
    private Instant createdAt;

    /**
     * When this config was last updated
     */
    private Instant updatedAt;

    /**
     * Version number for tracking changes
     */
    private Integer version = 1;
}

