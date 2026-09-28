package io.curiousoft.izinga.messaging.aiAgent.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Optional;

/**
 * MCP server configuration entry for an AI agent.
 *
 * SA-021-1: added Optional<Map<String,String>> headers for passing the agent-scope JWT
 * to OpenAI Responses API so it is forwarded on each tool-invocation request to the MCP server.
 *
 * HOTFIX: added @NoArgsConstructor so Spring Data MongoDB's MappingMongoConverter can
 * deserialize existing ai_agent_configs documents that contain mcpServers sub-documents.
 * Without it, startup crashed with NoSuchMethodException on McpServerConfig.<init>().
 * Pattern matches AiAgentConfig.HumanCorrection which uses the same @NoArgsConstructor
 * @AllArgsConstructor combination for identical reasons.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpServerConfig {
    private String type;
    @JsonProperty("server_label")
    private String serverLabel;
    @JsonProperty("server_description")
    private String serverDescription;
    @JsonProperty("server_url")
    private String serverUrl;
    @JsonProperty("require_approval")
    private String requireApproval;
    /**
     * SA-021-1: optional HTTP headers to include when OpenAI calls the MCP server.
     * Used to forward X-Agent-Scope JWT. Null means no additional headers.
     * Not serialised to JSON when null (JsonInclude.NON_NULL).
     */
    private Map<String, String> headers;

    /**
     * Convenience constructor without headers for backward compatibility.
     */
    public McpServerConfig(String type, String serverLabel, String serverDescription,
                           String serverUrl, String requireApproval) {
        this(type, serverLabel, serverDescription, serverUrl, requireApproval, null);
    }

    public Optional<Map<String, String>> getOptionalHeaders() {
        return Optional.ofNullable(headers);
    }
}
