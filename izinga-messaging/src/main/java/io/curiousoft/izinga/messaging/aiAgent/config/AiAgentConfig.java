package io.curiousoft.izinga.messaging.aiAgent.config;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Whether the agent is allowed to use mcp tools (e.g., external APIs)
     */
    private boolean useTools = true;

    /**
     * The audience this agent serves: DRIVER, CUSTOMER, or STORE.
     * Added in WA-LINES-02 — null on pre-existing driver_support documents until backfill.
     * SA-021-1 / SEC-WA02-01-D: sourced from this field (MongoDB), never from request payload.
     */
    private Audience audience;

    /**
     * For STORE agents: the store this config is scoped to.
     * Null for DRIVER and CUSTOMER agents, and for the store_support_default template.
     * Added in WA-LINES-02.
     */
    private String storeId;

    /**
     * MCP servers this agent is permitted to call.
     * If null or empty, the caller falls back to the hardcoded default server
     * (https://api.izinga.co.za/mcp) for backward compatibility.
     * Added in WA-LINES-02 — SA-021-1.
     */
    @Builder.Default
    private List<McpServerConfig> mcpServers = new ArrayList<>();

    /**
     * Tool names this agent is permitted to invoke.
     * If null or empty, all tools are permitted (backward-compatible behaviour for
     * driver_support and customer_support which predate this field).
     * Added in WA-LINES-02 — SA-021-8.
     */
    @Builder.Default
    private List<String> allowedTools = new ArrayList<>();

    /**
     * Whether this config is a template (not a live agent). active=false marks templates.
     * store_support_default has active=false; store_support_<storeId> clones have active=true.
     */
    public boolean isTemplate() {
        return Boolean.FALSE.equals(active);
    }

    /**
     * SEC-04: structured human correction entries. Stored as a list of {text, by, at},
     * never concatenated into the raw systemPrompt string.
     * Rendered as a clearly delimited block when building the prompt at inference time.
     */
    @Builder.Default
    private List<HumanCorrection> corrections = new ArrayList<>();

    /** SEC-04: immutable correction entry persisted on AiAgentConfig. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HumanCorrection {
        /** Sanitized correction text (max 500 chars, injection-stripped). */
        private String text;
        /** Phone or user identifier of the human agent who submitted the correction. */
        private String by;
        /** UTC timestamp when the correction was recorded. */
        private Instant at;
    }
}

