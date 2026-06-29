package io.curiousoft.izinga.messaging.aiAgent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Getter
@AllArgsConstructor
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
}
