package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistory;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
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
    private static final String AGENT_NAME = "driver_support";

    private final boolean enabled;
    private final String openAiApiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ConversationHistoryService conversationHistoryService;
    private final AiAgentConfigService agentConfigService;
    private final String systemPrompt;

    public AiCustomerServiceAgent(
            @Value("${ai.agent.enabled:false}") boolean enabled,
            @Value("${openai.api.key:}") String openAiApiKey,
            @Value("${ai.agent.model:gpt-4.1-mini}") String model,
            RestTemplate restTemplate,
            ConversationHistoryService conversationHistoryService,
            AiAgentConfigService agentConfigService) {
        this.enabled = enabled;
        this.openAiApiKey = openAiApiKey;
        this.model = model;
        this.restTemplate = restTemplate;
        this.conversationHistoryService = conversationHistoryService;
        this.agentConfigService = agentConfigService;
        this.systemPrompt = agentConfigService.getSystemPrompt(AGENT_NAME);
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
        if (!enabled) {
            LOG.debug("AI agent is disabled, skipping query from {}", from);
            return null;
        }

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

        if (userText.isBlank()) {
            LOG.warn("Received blank message body from {}", from);
            return null;
        }

        LOG.info("AI agent handling query from {}: {}", from, userText);

        try {
            // Load system prompt from database
            if (systemPrompt == null) {
                LOG.error("No system prompt found for agent: {}", AGENT_NAME);
                return null;
            }

            // Get or create conversation
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

            var mcpServerToolsForAgent = agentConfigService.getMcpToolsForAgent();
            Map<String, Object> requestBody = new HashMap<>(Map.of(
                    "model", model,
                    "input", messagesList
            ));

            var agent = agentConfigService.getActiveAgentConfig(AGENT_NAME);
            if (agent.isUseTools()) {
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
