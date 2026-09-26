package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.config.McpServerConfig;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistory;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class AiCustomerServiceAgent {

    private static final Logger LOG = LoggerFactory.getLogger(AiCustomerServiceAgent.class);

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/responses";
    /** REQ-11: removed hardcoded AGENT_NAME constant — resolved from LineContext at call time. */
    private static final String DEFAULT_AGENT_NAME = "driver_support";

    private final boolean enabled;
    private final String openAiApiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ConversationHistoryService conversationHistoryService;
    private final AiAgentConfigService agentConfigService;
    /** SA-021-17: attaches per-request scope JWT to MCP server URLs. */
    private final StoreScopeJwtService storeScopeJwtService;

    public AiCustomerServiceAgent(
            @Value("${ai.agent.enabled:false}") boolean enabled,
            @Value("${openai.api.key:}") String openAiApiKey,
            @Value("${ai.agent.model:gpt-4.1-mini}") String model,
            RestTemplate restTemplate,
            ConversationHistoryService conversationHistoryService,
            AiAgentConfigService agentConfigService,
            StoreScopeJwtService storeScopeJwtService) {
        this.enabled = enabled;
        this.openAiApiKey = openAiApiKey;
        this.model = model;
        this.restTemplate = restTemplate;
        this.conversationHistoryService = conversationHistoryService;
        this.agentConfigService = agentConfigService;
        this.storeScopeJwtService = storeScopeJwtService;
    }

    /**
     * SA-021-17: Build the MCP tools list with scope JWTs appended to each server URL.
     * SEC-WA02-01-D: audience is sourced from AiAgentConfig (MongoDB), never from request payload.
     *
     * @param agentName the agent config key
     * @param storeId   the store ID (null for DRIVER/CUSTOMER agents)
     */
    protected List<McpServerConfig> buildScopedMcpTools(String agentName, String storeId) {
        var configs = agentConfigService.getMcpToolsForAgent(agentName);
        var agentConfig = agentConfigService.getActiveAgentConfig(agentName);
        var audience = (agentConfig != null) ? agentConfig.getAudience() : null;
        List<McpServerConfig> scoped = new ArrayList<>(configs.size());
        for (McpServerConfig cfg : configs) {
            try {
                String scopedUrl = storeScopeJwtService.buildScopedUrl(cfg.getServerUrl(), storeId, audience);
                scoped.add(new McpServerConfig(cfg.getType(), cfg.getServerLabel(),
                        cfg.getServerDescription(), scopedUrl, cfg.getRequireApproval(), cfg.getHeaders()));
            } catch (Exception e) {
                LOG.warn("SA-021-17: failed to build scope token for agent={} server={} — using base URL",
                        agentName, cfg.getServerLabel(), e);
                scoped.add(cfg);
            }
        }
        return scoped;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Handles an incoming WhatsApp message from a driver and returns an AI-generated customer service reply.
     * Includes conversation history for context and loads system prompt from database.
     *
     * @param message the incoming WhatsApp message payload
     * @param from    the sender's phone number
     * @param driverName the driver's name (optional)
     * @return AI-generated reply string, or null if disabled or an error occurs
     */
    public String handleWhatsappQuery(WhatsappWebhookPayload.Value.Message message, String from, String driverName) {
        if ((message == null || message.getText() == null || message.getText().getBody() == null) && message.getButton() == null) {
            LOG.warn("Received null or empty message from {}", from);
            return null;
        }

        String userText = Optional.ofNullable(message.getText())
                .map(it -> it.getBody().trim())
                .orElse("");
        if (userText.isBlank()) {
            userText = message.getButton().getText().trim();
        }
        return handleWhatsappQuery(userText, from, driverName);
    }

    public String handleWhatsappQuery(String userText, String from, String driverName) {
        if (!enabled) {
            LOG.debug("AI agent is disabled, skipping query from {}", from);
            return null;
        }

        LOG.info("AI agent handling query from {}: {}", from, userText);
        // REQ-20: agentName is passed in, never hardcoded
        var systemPrompt = agentConfigService.getSystemPrompt(DEFAULT_AGENT_NAME);
        try {
            // Load system prompt from database
            if (systemPrompt == null) {
                LOG.error("No system prompt found for agent: {}", DEFAULT_AGENT_NAME);
                return null;
            }

            // Get or create conversation (no agentName scoping for legacy callers)
            ConversationHistory conversation = conversationHistoryService
                .getOrCreateConversation(from, driverName != null ? driverName : "Driver");

            // Add user message to history
            conversationHistoryService.addUserMessage(conversation, userText);

            var systemPromptWithContext = systemPrompt + " You are helping " + conversation.getDriverName() +
                    " with their phone number " + conversation.getDriverPhoneNumber() + " as the only number you will use and assist with their queries. " +
                            "Do not share information with anyone else not using this number and do not use a different phone number to assist with queries. This is a security " +
                            "measure to ensure you are assisting the correct driver and not sharing information with the wrong people.";

            // Build messages list: system prompt + context messages + current user message
            List<Map<String, Object>> messagesList = new ArrayList<>();
            messagesList.add(Map.of("role", "system", "content", systemPromptWithContext));

            // Add conversation context (last N messages)
            var contextMessages = conversationHistoryService.getContextMessages(conversation);
            for (var msg : contextMessages) {
                    messagesList.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            // SA-021-3 + SA-021-17: per-agent mcpServers with scope JWT in each URL
            var mcpServerToolsForAgent = buildScopedMcpTools(DEFAULT_AGENT_NAME, null);
            Map<String, Object> requestBody = new HashMap<>(Map.of(
                    "model", model,
                    "input", messagesList
            ));

            var agent = agentConfigService.getActiveAgentConfig(DEFAULT_AGENT_NAME);
            if (agent != null && agent.isUseTools()) {
                requestBody.put("tools", mcpServerToolsForAgent);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_CHAT_URL, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String reply = extractReply(response.getBody());
                if (reply != null) {
                    // Add AI response to conversation history
                    conversationHistoryService.addAssistantMessage(conversation, reply);
                    LOG.info("AI agent replied to {} with {} chars of context", from, contextMessages.size());
                    return reply;
                }
            } else {
                LOG.warn("OpenAI returned non-2xx status {} for query from {}", response.getStatusCode(), from);
                return null;
            }

        } catch (Exception e) {
            LOG.error("AI agent failed to handle query from {}: {}", from, e.getMessage(), e);
            return null;
        }

        return null;
    }

    /**
     * Legacy method for backward compatibility (without driverName parameter)
     */
    public String handleWhatsappQuery(WhatsappWebhookPayload.Value.Message message, String from) {
        return handleWhatsappQuery(message, from, null);
    }

    /**
     * REQ-20: agent-aware entry point. Routes to the correct agent config and conversation history.
     * SA-6: agentName comes from LineContext, never a constant.
     */
    public String handleWhatsappQueryForAgent(WhatsappWebhookPayload.Value.Message message, String from,
                                               String driverName, String agentName) {
        if ((message == null || message.getText() == null || message.getText().getBody() == null) && message.getButton() == null) {
            LOG.warn("Received null or empty message from {}", from);
            return null;
        }
        String userText = Optional.ofNullable(message.getText())
                .map(it -> it.getBody().trim())
                .orElse("");
        if (userText.isBlank() && message.getButton() != null) {
            userText = message.getButton().getText().trim();
        }
        return handleWhatsappQueryForAgent(userText, from, driverName, agentName);
    }

    /**
     * REQ-20: agent-aware text overload.
     */
    public String handleWhatsappQueryForAgent(String userText, String from, String driverName, String agentName) {
        if (!enabled) {
            LOG.debug("AI agent is disabled, skipping query from {}", from);
            return null;
        }
        String resolvedAgent = (agentName != null && !agentName.isBlank()) ? agentName : DEFAULT_AGENT_NAME;
        LOG.info("AI agent '{}' handling query from {}: {}", resolvedAgent, from, userText);

        var systemPrompt = agentConfigService.getSystemPrompt(resolvedAgent);
        try {
            if (systemPrompt == null) {
                LOG.error("No system prompt found for agent: {}", resolvedAgent);
                return null;
            }

            // REQ-15: per-agent conversation history
            ConversationHistory conversation = conversationHistoryService
                    .getOrCreateConversation(from, driverName != null ? driverName : "User", resolvedAgent);
            conversationHistoryService.addUserMessage(conversation, userText);

            var systemPromptWithContext = systemPrompt + " You are helping " + conversation.getDriverName() +
                    " with their phone number " + conversation.getDriverPhoneNumber() +
                    " as the only number you will use and assist with their queries.";

            List<Map<String, Object>> messagesList = new ArrayList<>();
            messagesList.add(Map.of("role", "system", "content", systemPromptWithContext));
            var contextMessages = conversationHistoryService.getContextMessages(conversation);
            for (var msg : contextMessages) {
                messagesList.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            // SA-021-3 + SA-021-17: per-agent mcpServers with scope JWT in each URL (no storeId for DRIVER/CUSTOMER)
            var mcpServerToolsForAgent = buildScopedMcpTools(resolvedAgent, null);
            Map<String, Object> requestBody = new HashMap<>(Map.of("model", model, "input", messagesList));

            var agent = agentConfigService.getActiveAgentConfig(resolvedAgent);
            if (agent != null && agent.isUseTools()) {
                requestBody.put("tools", mcpServerToolsForAgent);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_CHAT_URL, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String reply = extractReply(response.getBody());
                if (reply != null) {
                    conversationHistoryService.addAssistantMessage(conversation, reply);
                    LOG.info("AI agent '{}' replied to {} with {} chars of context",
                            resolvedAgent, from, contextMessages.size());
                    return reply;
                }
            } else {
                LOG.warn("OpenAI returned non-2xx status {} for query from {}", response.getStatusCode(), from);
            }
        } catch (Exception e) {
            LOG.error("AI agent '{}' failed to handle query from {}: {}", resolvedAgent, from, e.getMessage(), e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractReply(Map<?, ?> responseBody) {
        try {
            var output = (List<Map<String, Object>>) responseBody.get("output");
            if (output == null || output.isEmpty()) return null;
            // Find the message object in the output array
            Map<String, Object> messageObj = null;
            for (Map<String, Object> item : output) {
                if ("message".equals(item.get("type"))) {
                    messageObj = item;
                    break;
                }
            }
            if (messageObj == null) return null;
            var content = (List<Map<String, Object>>) messageObj.get("content");
            if (content == null || content.isEmpty()) return null;
            // Extract text from the first content item
            for (Map<String, Object> contentItem : content) {
                if ("output_text".equals(contentItem.get("type"))) {
                    return (String) contentItem.get("text");
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to extract AI reply from response body", e);
        }
        return null;
    }

}
