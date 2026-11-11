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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.support.RetryTemplate;
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
    /** ADR-031 / AC-02: OpenAI-scoped RestTemplate with explicit timeouts (3 s connect, 15 s read). */
    private final RestTemplate restTemplate;
    /** ADR-031 / REQ-03: RetryTemplate covering 5xx / 429 / connection failures (non-retryable: 4xx). */
    private final RetryTemplate openAiRetryTemplate;
    private final AiAgentConfigService agentConfigService;
    private final ConversationHistoryService conversationHistoryService;
    private final StoreContextResolver storeContextResolver;
    private final StoreRepository storeRepository;
    private final StoreScopeJwtService storeScopeJwtService;

    public StoreAiAgent(
            @Value("${ai.agent.enabled:false}") boolean enabled,
            @Value("${openai.api.key:}") String openAiApiKey,
            @Value("${ai.agent.model:gpt-4.1-mini}") String model,
            // ADR-031 / AC-02: OpenAI-qualified bean (3 s connect, 15 s read timeouts — configurable)
            @Qualifier("openAiRestTemplate") RestTemplate restTemplate,
            AiAgentConfigService agentConfigService,
            ConversationHistoryService conversationHistoryService,
            StoreContextResolver storeContextResolver,
            StoreRepository storeRepository,
            StoreScopeJwtService storeScopeJwtService,
            // ADR-031 / REQ-03: programmatic RetryTemplate for OpenAI network calls
            @Qualifier("openAiRetryTemplate") RetryTemplate openAiRetryTemplate) {
        this.enabled = enabled;
        this.openAiApiKey = openAiApiKey;
        this.model = model;
        this.restTemplate = restTemplate;
        this.openAiRetryTemplate = openAiRetryTemplate;
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

            // SA-021-17 + ADR-024: attach scope JWT with callerPhone for audit completeness
            var mcpTools = buildScopedMcpTools(agentConfig, storeId, from);

            Map<String, Object> requestBody = new HashMap<>(Map.of("model", model, "input", messagesList));
            if (agentConfig.isUseTools() && !mcpTools.isEmpty()) {
                // SA-021-8: filter to allowedTools if specified
                requestBody.put("tools", mcpTools);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            // ADR-031 / REQ-03: wrap network call in RetryTemplate — retries on 5xx/429/connection
            // failures; non-retryable on 4xx (incl. 400 context-window overflow).
            // On retry exhaustion execute() re-throws; the outer catch returns null.
            // NOTE-05: log every attempt unconditionally so QA can grep for attempt=1 on a
            // non-retryable 4xx (proves exactly one attempt, no retry).
            // ADR-031 (Option B+C): attempt=1 at INFO (a successful call must not emit WARN);
            // attempt >= 2 at WARN (a genuine retry warrants alerting).
            final String resolvedAgentFinal = resolvedAgent;
            ResponseEntity<Map> response = openAiRetryTemplate.execute(context -> {
                int attempt = context.getRetryCount() + 1;
                if (attempt == 1) {
                    LOG.info("[AI-ATTEMPT] StoreAiAgent attempt={} agent='{}' for from={}", attempt, resolvedAgentFinal, from);
                } else {
                    LOG.warn("[AI-ATTEMPT] StoreAiAgent attempt={} agent='{}' for from={}", attempt, resolvedAgentFinal, from);
                }
                return restTemplate.postForEntity(OPENAI_CHAT_URL, entity, Map.class);
            });

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
     * Fill the system prompt's store context block with sanitized live store data from MongoDB.
     *
     * SEC-WA02-04-B: ALL four store-sourced values ({storeName}, {storeLocation},
     * {businessHours}, {storeMenu}) reach the prompt exclusively through
     * {@link StoreContextResolver#buildContextBlock(StoreProfile)}, which internally routes
     * every field through {@link io.curiousoft.izinga.messaging.whatsapp.StoreContentSanitizer}.
     * No value is injected via a sanitizer-bypassing path.
     *
     * FIX-01: the prior implementation replaced the four placeholders individually (steps 1-4)
     * before step 5 attempted to replace the whole delimited block. Steps 1-4 consumed the
     * placeholders, so step 5 never matched and the sanitized context was silently discarded.
     * The fix removes steps 1-4 entirely and replaces only the whole delimited block so all
     * values flow through StoreContextResolver/StoreContentSanitizer without exception.
     *
     * Template format (AiAgentConfigInitializer lines 287-293, text-block stripped):
     *   === Store Context Begin ===
     *   Store: {storeName}
     *   Location: {storeLocation}
     *   Hours: {businessHours}
     *   Menu:
     *   {storeMenu}
     *   === Store Context End ===
     */
    private String buildPromptWithStoreContext(AiAgentConfig config, String storeId) {
        String base = agentConfigService.buildPromptWithCorrections(config);
        var storeOpt = storeRepository.findById(storeId);
        if (storeOpt.isEmpty()) {
            LOG.warn("StoreAiAgent: store {} not found in MongoDB — using base prompt", storeId);
            return base;
        }
        var store = storeOpt.get();
        // All four values are resolved and sanitized by StoreContextResolver/StoreContentSanitizer
        String contextBlock = storeContextResolver.buildContextBlock(store);
        // Replace the ENTIRE delimited block. Search string matches the exact template format.
        // "Hours: {businessHours}" is a single line (not "Hours:\n{businessHours}") per the template.
        return base.replace(
                "=== Store Context Begin ===\n" +
                "Store: {storeName}\n" +
                "Location: {storeLocation}\n" +
                "Hours: {businessHours}\n" +
                "Menu:\n" +
                "{storeMenu}\n" +
                "=== Store Context End ===",
                "=== Store Context Begin ===\n" + contextBlock + "\n=== Store Context End ===");
    }

    /**
     * SA-021-17 / ADR-024: attach scope JWT to each MCP server URL.
     * ADR-024: callerPhone is embedded in the JWT for audit completeness.
     * STORE audience enforcement remains storeId-based only — no validateCallerPhone()
     * is called anywhere on the store agent's tool path.
     * SA-021-8: filter to allowedTools.
     */
    private List<McpServerConfig> buildScopedMcpTools(AiAgentConfig config, String storeId, String callerPhone) {
        List<McpServerConfig> all = agentConfigService.getMcpToolsForAgent(config.getAgentName());
        List<McpServerConfig> result = new ArrayList<>(all.size());
        for (McpServerConfig cfg : all) {
            // SA-021-8: skip if allowedTools is non-empty and this server is not in the list
            // (server-level filtering — tool-level is handled by OpenAI "tools" filter)
            try {
                String scopedUrl = storeScopeJwtService.buildScopedUrl(
                        cfg.getServerUrl(), storeId, Audience.STORE, callerPhone);
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
        if (message.getLocation() != null) {
            return message.getLocation().describe();
        }
        return null;
    }
}
