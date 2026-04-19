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

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
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

        if (message == null || message.getText() == null || message.getText().getBody() == null) {
            LOG.warn("Received null or empty message from {}", from);
            return null;
        }

        String userText = message.getText().getBody().trim();
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

            // Build messages list: system prompt + context messages + current user message
            List<Map<String, Object>> messagesList = new ArrayList<>();
            messagesList.add(Map.of("role", "system", "content", systemPrompt));

            // Add conversation context (last N messages)
            var contextMessages = conversationHistoryService.getContextMessages(conversation);
            for (var msg : contextMessages) {
                    messagesList.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", messagesList,
                    "max_tokens", 500,
                    "temperature", 0.4
            );

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
            var choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            var messageMap = (Map<String, Object>) choices.get(0).get("message");
            if (messageMap == null) return null;
            return (String) messageMap.get("content");
        } catch (Exception e) {
            LOG.error("Failed to extract AI reply from response body", e);
            return null;
        }
    }
}
