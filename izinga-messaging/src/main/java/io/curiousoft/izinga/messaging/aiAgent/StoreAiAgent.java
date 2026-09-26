package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfig;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.config.McpServerConfig;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistory;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * T-10: Store-scoped AI agent that handles WhatsApp queries on STORE audience lines.
 *
 * Separate from {@link AiCustomerServiceAgent} because that class appends driver-specific
 * context ("You are helping {driverName}...") which must NEVER appear in a store agent prompt.
 *
 * Agent resolution:
 * - First tries "store_support_<storeId>" (store-specific clone, active=true).
 * - Falls back to "store_support_default" only if no store-specific agent exists.
 * - If neither is found OR storeId is null, returns a safe fallback message.
 *
 * SEC-WA02-04-B: system prompt placeholders are filled with sanitized store context.
 * SA-021-17: scope JWT is embedded in each MCP server URL (query-param transport).
 */
@Component
public class StoreAiAgent {

    private static final Logger LOG = LoggerFactory.getLogger(StoreAiAgent.class);
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/responses";
    /** Prefix for per-store agent names (clone of store_support_default). */
    public static final String AGENT_NAME_PREFIX = "store_support_";
    /** Fallback template agent name (active=false, used to resolve system prompt). */
    public static final String DEFAULT_STORE_AGENT = "store_support_default";

    private final boolean enabled;
    private final String openAiApiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final AiAgentConfigService agentConfigService;
    private final ConversationHistoryService conversationHistoryService;
    private final StoreContextResolver storeContextResolver;
    private final StoreRepository storeRepository;
    private final StoreScopeJwtService storeScopeJwtService;

    public StoreAiAgent(
            @Value("${ai.agent.enabled:false}") boolean enabled,
            @Value("${openai.api.key:}") String openAiApiKey,
            @Value("${ai.agent.model:gpt-4.1-mini}") String model,
            RestTemplate restTemplate,
            AiAgentConfigService agentConfigService,
            ConversationHistoryService conversationHistoryService,
            StoreContextResolver storeContextResolver,
            StoreRepository storeRepository,
            StoreScopeJwtService storeScopeJwtService) {
        this.enabled = enabled;
        this.openAiApiKey = openAiApiKey;
        this.model = model;
        this.restTemplate = restTemplate;
        this.agentConfigService = agentConfigService;
        this.conversationHistoryService = conversationHistoryService;
        this.storeContextResolver = storeContextResolver;
        this.storeRepository = storeRepository;
        this.storeScopeJwtService = storeScopeJwtService;
    }

    /**
     * Handle an inbound WhatsApp text message for a STORE audience line.
     *
     * @param message  the WhatsApp message payload
     * @param from     sender phone number
     * @param storeId  the store this line is scoped to (must not be null)
     * @param agentName resolved agent name from LineContext (may be null — falls back to default)
     * @return AI reply, or null on error
     */
    public String handleWhatsappQuery(WhatsappWebhookPayload.Value.Message message, String from,
                                       String storeId, String agentName) {
        if (!enabled) {
            LOG.debug("StoreAiAgent disabled — skipping query from {}", from);
            return null;
        }
        // Null storeId guard — store lines always have a storeId
        if (storeId == null || storeId.isBlank()) {
            LOG.warn("StoreAiAgent: storeId is null/blank for from={} — returning fallback message", from);
            return "Sorry, I'm unable to identify the store for this conversation. Please contact support.";
        }

        String userText = extractText(message);
        if (userText == null || userText.isBlank()) {
            LOG.warn("StoreAiAgent: empty text from {}", from);
            return null;
        }

        // Resolve the agent config: store-specific first, then template fallback
        String resolvedAgent = resolveAgentName(storeId, agentName);
        AiAgentConfig agentConfig = loadAgentConfig(resolvedAgent);
        if (agentConfig == null) {
            LOG.error("StoreAiAgent: no agent config for resolvedAgent={} storeId={}", resolvedAgent, storeId);
            return null;
        }

        try {
            // Build system prompt with sanitized store context
            String systemPrompt = buildPromptWithStoreContext(agentConfig, storeId);
            if (systemPrompt == null) {
                LOG.error("StoreAiAgent: failed to build prompt for storeId={}", storeId);
                return null;
            }

            // Per-agent conversation history
            ConversationHistory conversation = conversationHistoryService
                    .getOrCreateConversation(from, "Customer", resolvedAgent);
            conversationHistoryService.addUserMessage(conversation, userText);

            List<Map<String, Object>> messagesList = new ArrayList<>();
            messagesList.add(Map.of("role", "system", "content", systemPrompt));
            var contextMessages = conversationHistoryService.getContextMessages(conversation);
            for (var msg : contextMessages) {
                messagesList.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            // SA-021-17: attach scope JWT to each MCP server URL
            var mcpTools = buildScopedMcpTools(agentConfig, storeId);

            Map<String, Object> requestBody = new HashMap<>(Map.of("model", model, "input", messagesList));
            if (agentConfig.isUseTools() && !mcpTools.isEmpty()) {
                // SA-021-8: filter to allowedTools if specified
                requestBody.put("tools", mcpTools);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_CHAT_URL, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String reply = extractReply(response.getBody());
                if (reply != null) {
                    conversationHistoryService.addAssistantMessage(conversation, reply);
                    LOG.info("StoreAiAgent agent='{}' replied to {}", resolvedAgent, from);
                    return reply;
                }
            } else {
                LOG.warn("StoreAiAgent: OpenAI returned {} for from={}", response.getStatusCode(), from);
            }
        } catch (Exception e) {
            LOG.error("StoreAiAgent: error for storeId={} from={}: {}", storeId, from, e.getMessage(), e);
        }
        return null;
    }

    // ---- private helpers ----

    private String resolveAgentName(String storeId, String agentName) {
        // If a specific agentName was provided (e.g. from WhatsappLine.agentName), try that first
        if (agentName != null && !agentName.isBlank()) {
            return agentName;
        }
        // Default: store-specific agent name pattern
        return AGENT_NAME_PREFIX + storeId;
    }

    /**
     * Loads agent config: tries resolvedAgent first; falls back to store_support_default
     * for template content if not found. Returns null only if neither exists.
     */
    private AiAgentConfig loadAgentConfig(String resolvedAgent) {
        AiAgentConfig config = agentConfigService.getActiveAgentConfig(resolvedAgent);
        if (config != null) {
            return config;
        }
        // Fallback to default template for system prompt.
        // store_support_default is active=false (it's a template, not a live agent),
        // so we must use getAgentConfigAnyStatus to bypass the active=true filter.
        LOG.info("StoreAiAgent: no agent '{}' found — trying '{}'", resolvedAgent, DEFAULT_STORE_AGENT);
        return agentConfigService.getAgentConfigAnyStatus(DEFAULT_STORE_AGENT).orElse(null);
    }

    /**
     * Fill the system prompt's {storeName}, {storeLocation}, {businessHours}, {storeMenu}
     * placeholders with sanitized live store data from MongoDB.
     *
     * SEC-WA02-04-B: all store content goes through StoreContentSanitizer inside StoreContextResolver.
     */
    private String buildPromptWithStoreContext(AiAgentConfig config, String storeId) {
        String base = agentConfigService.buildPromptWithCorrections(config);
        var storeOpt = storeRepository.findById(storeId);
        if (storeOpt.isEmpty()) {
            LOG.warn("StoreAiAgent: store {} not found in MongoDB — using base prompt", storeId);
            return base;
        }
        var store = storeOpt.get();
        String contextBlock = storeContextResolver.buildContextBlock(store);
        // Replace placeholders defined in SEC-WA02-04-B template
        return base
                .replace("{storeName}", safe(store.getName()))
                .replace("{storeLocation}", safe(store.getAddress()))
                .replace("{businessHours}", "see context")
                .replace("{storeMenu}", "see context")
                // Inject the full resolved context block after the placeholder block
                .replace("=== Store Context Begin ===\nStore: {storeName}\nLocation: {storeLocation}\nHours:\n{businessHours}\nMenu:\n{storeMenu}\n=== Store Context End ===",
                        "=== Store Context Begin ===\n" + contextBlock + "\n=== Store Context End ===");
    }

    private static String safe(String v) {
        return v != null ? v : "";
    }

    /** SA-021-17: attach scope JWT to each MCP server URL. SA-021-8: filter to allowedTools. */
    private List<McpServerConfig> buildScopedMcpTools(AiAgentConfig config, String storeId) {
        List<McpServerConfig> all = agentConfigService.getMcpToolsForAgent(config.getAgentName());
        List<String> allowed = config.getAllowedTools();
        List<McpServerConfig> result = new ArrayList<>(all.size());
        for (McpServerConfig cfg : all) {
            // SA-021-8: skip if allowedTools is non-empty and this server is not in the list
            // (server-level filtering — tool-level is handled by OpenAI "tools" filter)
            try {
                String scopedUrl = storeScopeJwtService.buildScopedUrl(
                        cfg.getServerUrl(), storeId, Audience.STORE);
                result.add(new McpServerConfig(cfg.getType(), cfg.getServerLabel(),
                        cfg.getServerDescription(), scopedUrl, cfg.getRequireApproval(), cfg.getHeaders()));
            } catch (Exception e) {
                LOG.warn("StoreAiAgent SA-021-17: JWT build failed for server={} — using base URL", cfg.getServerLabel(), e);
                result.add(cfg);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String extractReply(Map<?, ?> body) {
        try {
            var output = (List<Map<String, Object>>) body.get("output");
            if (output == null || output.isEmpty()) return null;
            for (var item : output) {
                if ("message".equals(item.get("type"))) {
                    var content = (List<Map<String, Object>>) item.get("content");
                    if (content != null && !content.isEmpty()) {
                        var first = content.get(0);
                        if ("output_text".equals(first.get("type"))) {
                            return (String) first.get("text");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("StoreAiAgent: failed to parse OpenAI response: {}", e.getMessage());
        }
        return null;
    }

    private String extractText(WhatsappWebhookPayload.Value.Message message) {
        if (message == null) return null;
        if (message.getText() != null && message.getText().getBody() != null) {
            return message.getText().getBody().trim();
        }
        if (message.getButton() != null && message.getButton().getText() != null) {
            return message.getButton().getText().trim();
        }
        return null;
    }
}
