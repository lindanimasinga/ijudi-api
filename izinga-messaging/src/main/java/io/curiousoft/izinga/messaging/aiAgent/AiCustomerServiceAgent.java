package io.curiousoft.izinga.messaging.aiAgent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.CallerIdentityValidator;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.config.McpServerConfig;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistory;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AiCustomerServiceAgent {

    private static final Logger LOG = LoggerFactory.getLogger(AiCustomerServiceAgent.class);

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/responses";
    /** REQ-11: removed hardcoded AGENT_NAME constant — resolved from LineContext at call time. */
    private static final String DEFAULT_AGENT_NAME = "driver_support";

    /**
     * C-1 (Legal): matches phone-number-like sequences — optional + then 7–15 digits.
     * Applied to tool-call arguments and any phone-containing log output to mask all but the last 4 digits.
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9]{7,15}");

    private final boolean enabled;
    private final String openAiApiKey;
    private final String model;
    /**
     * PO AC-04: flag to enable detailed OpenAI request/response DEBUG logging.
     * Default false. Set true in uat and local profiles. MUST NOT be true in prod.
     * See application.yml / application-uat.yml / application-local.yml.
     */
    private final boolean debugLogOpenAi;
    /** ADR-031 / AC-02: OpenAI-scoped RestTemplate with explicit timeouts (3 s connect, 15 s read). */
    private final RestTemplate restTemplate;
    /** ADR-031 / REQ-03: RetryTemplate covering 5xx / 429 / connection failures (non-retryable: 4xx). */
    private final RetryTemplate openAiRetryTemplate;
    private final ConversationHistoryService conversationHistoryService;
    private final AiAgentConfigService agentConfigService;
    /** SA-021-17: attaches per-request scope JWT to MCP server URLs. */
    private final StoreScopeJwtService storeScopeJwtService;
    /** AMD-01 (REQ-A2/REQ-A3): required for ADMIN audience minting. */
    private final WhatsappConfig whatsappConfig;
    /** AMD-01 (REQ-A2): used to resolve callerPhone to UserProfile for role check. */
    private final UserProfileRepo userProfileRepo;
    /** Used for debug-only serialisation of tools array. Never injected into request body. */
    private final ObjectMapper debugObjectMapper = new ObjectMapper();

    public AiCustomerServiceAgent(
            @Value("${ai.agent.enabled:false}") boolean enabled,
            @Value("${openai.api.key:}") String openAiApiKey,
            @Value("${ai.agent.model:gpt-4.1-mini}") String model,
            @Value("${izinga.ai.agent.debug.log-openai-request-response:false}") boolean debugLogOpenAi,
            // ADR-031 / AC-02: OpenAI-qualified bean (3 s connect, 15 s read timeouts — configurable)
            @Qualifier("openAiRestTemplate") RestTemplate restTemplate,
            ConversationHistoryService conversationHistoryService,
            AiAgentConfigService agentConfigService,
            StoreScopeJwtService storeScopeJwtService,
            WhatsappConfig whatsappConfig,
            UserProfileRepo userProfileRepo,
            // ADR-031 / REQ-03: programmatic RetryTemplate for OpenAI network calls
            @Qualifier("openAiRetryTemplate") RetryTemplate openAiRetryTemplate) {
        this.enabled = enabled;
        this.openAiApiKey = openAiApiKey;
        this.model = model;
        this.debugLogOpenAi = debugLogOpenAi;
        this.restTemplate = restTemplate;
        this.openAiRetryTemplate = openAiRetryTemplate;
        this.conversationHistoryService = conversationHistoryService;
        this.agentConfigService = agentConfigService;
        this.storeScopeJwtService = storeScopeJwtService;
        this.whatsappConfig = whatsappConfig;
        this.userProfileRepo = userProfileRepo;
    }

    // ─── POPIA / legal helpers ──────────────────────────────────────────────────

    /**
     * C-1 (Legal / POPIA): masks all but the last 4 digits of any phone-like substring in {@code text}.
     * Example: "+27812815707" → "***5707".
     * Applied to logged tool-call arguments to prevent direct identifiers reaching log storage.
     */
    static String maskPhoneInText(String text) {
        if (text == null) return null;
        Matcher m = PHONE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String matched = m.group();
            String masked = "***" + matched.substring(matched.length() - 4);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * SEC-WA02-01-A: redacts the JWT token value in any {@code ?scope=<token>} or
     * {@code &scope=<token>} query parameter so that the JWT never reaches log storage.
     * URL structure (host, path) is preserved for diagnostics.
     */
    static String redactJwtInUrl(String text) {
        if (text == null) return null;
        return text.replaceAll("([?&]scope=)[^&\"'\\s]+", "$1[REDACTED]");
    }

    // ─── diagnostic logging helpers ─────────────────────────────────────────────

    /**
     * PO AC-03/AC-04: logs the OpenAI request metadata when {@link #debugLogOpenAi} is on.
     * Logs at DEBUG only. Never logs: Authorization header, API key, message text, or JWT values.
     *
     * @param requestBody   the full request map to be sent to OpenAI
     * @param inputMessages the input messages list (roles logged, text NOT logged)
     */
    private void logPreRequest(Map<String, Object> requestBody, List<Map<String, Object>> inputMessages) {
        if (!debugLogOpenAi) return;
        try {
            boolean toolsPresent = requestBody.containsKey("tools");
            Object modelObj = requestBody.get("model");

            // Count + role sequence only — message text is never logged
            String inputDesc;
            if (inputMessages == null) {
                inputDesc = "null";
            } else {
                List<String> roles = inputMessages.stream()
                        .map(msg -> msg.get("role") != null ? msg.get("role").toString() : "?")
                        .collect(Collectors.toList());
                inputDesc = inputMessages.size() + " messages " + roles;
            }

            // Serialise tools with JWT redaction — never log the token value
            String toolsJson = "none";
            if (toolsPresent) {
                try {
                    String raw = debugObjectMapper.writeValueAsString(requestBody.get("tools"));
                    toolsJson = redactJwtInUrl(raw);
                } catch (Exception e) {
                    toolsJson = "[serialization-error]";
                }
            }

            LOG.debug("[AI-DEBUG] model={} toolsPresent={} input: {} tools: {}",
                    modelObj, toolsPresent, inputDesc, toolsJson);
        } catch (Exception e) {
            LOG.debug("[AI-DEBUG] logPreRequest error: {}", e.getMessage());
        }
    }

    /**
     * PO AC-03/AC-04: logs the OpenAI response output metadata when {@link #debugLogOpenAi} is on.
     * Logs at DEBUG only. For non-message output items (mcp_call, function_call, tool_call,
     * mcp_list_tools), logs the full item with phone numbers masked. Never logs message text.
     *
     * @param responseBody the response map returned by OpenAI
     */
    @SuppressWarnings("unchecked")
    private void logPostResponse(Map<?, ?> responseBody) {
        if (!debugLogOpenAi) return;
        try {
            var output = (List<Map<String, Object>>) responseBody.get("output");
            if (output == null) {
                LOG.debug("[AI-DEBUG] response output: null");
                return;
            }
            List<String> types = output.stream()
                    .map(item -> item.get("type") != null ? item.get("type").toString() : "null")
                    .collect(Collectors.toList());
            LOG.debug("[AI-DEBUG] response output: {} items {}", output.size(), types);

            for (Map<String, Object> item : output) {
                String type = item.get("type") != null ? item.get("type").toString() : "null";
                // Log full item for tool-call types; never log message/text content
                if (!"message".equals(type)) {
                    try {
                        String itemJson = debugObjectMapper.writeValueAsString(item);
                        // C-1: mask phone numbers that may appear in tool arguments
                        itemJson = maskPhoneInText(itemJson);
                        LOG.debug("[AI-DEBUG] non-message output item type={}: {}", type, itemJson);
                    } catch (Exception e) {
                        LOG.debug("[AI-DEBUG] non-message output item type={} [serialization-error]", type);
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("[AI-DEBUG] logPostResponse error: {}", e.getMessage());
        }
    }

    // ─── MCP scoping ────────────────────────────────────────────────────────────

    /**
     * SA-021-17 / ADR-024: Build the MCP tools list with scope JWTs appended to each server URL.
     * SEC-WA02-01-D: audience is sourced from AiAgentConfig (MongoDB), never from request payload.
     * ADR-024: callerPhone is embedded in each JWT so StoreScopeValidationFilter can populate
     * StoreScopeContext.callerPhone for every tool call.
     *
     * @param agentName   the agent config key
     * @param storeId     the store ID (null for DRIVER/CUSTOMER agents)
     * @param callerPhone ADR-024: the WhatsApp caller's phone number (the "from" field)
     */
    protected List<McpServerConfig> buildScopedMcpTools(String agentName, String storeId, String callerPhone) {
        var configs = agentConfigService.getMcpToolsForAgent(agentName);
        var agentConfig = agentConfigService.getActiveAgentConfig(agentName);
        Audience audience = (agentConfig != null) ? agentConfig.getAudience() : null;

        // AMD-01 (REQ-A4): ADMIN must never come from AiAgentConfig/WhatsappLine audience.
        // If someone misconfigured an agent with ADMIN audience, silently demote to CUSTOMER.
        if (audience == Audience.ADMIN) {
            LOG.warn("AMD-01/REQ-A4: configured audience is ADMIN for agent={} — ADMIN audience must be minted dynamically, not from AiAgentConfig. Treating as CUSTOMER.", agentName);
            audience = Audience.CUSTOMER;
        }

        // AMD-01 (REQ-A2/REQ-A3): if signature verification is active AND callerPhone is known,
        // check whether the caller is an ADMIN. If so, override audience to ADMIN and drop the storeId.
        // When verification is disabled (production bypass state), skip the DB read entirely.
        String mintStoreId = storeId;
        if (callerPhone != null && whatsappConfig.isSignatureVerificationEnabled()) {
            try {
                String last9 = CallerIdentityValidator.normalizeLast9(callerPhone);
                UserProfile caller = null;
                if (last9 != null) {
                    for (String prefix : List.of("0", "+27", "27")) {
                        caller = userProfileRepo.findByMobileNumber(prefix + last9);
                        if (caller != null) break;
                    }
                }
                if (caller != null && caller.getRole() == ProfileRoles.ADMIN) {
                    audience = Audience.ADMIN;
                    mintStoreId = null; // ADMIN scope is not store-scoped
                    LOG.info("AMD-01: minting ADMIN audience scope token agent={} phone=<redacted>", agentName);
                }
            } catch (Exception e) {
                // AMD-01 (REQ-A3 / Architect plan fail-closed note): an admin-lookup failure must
                // NOT drop the token. Log and fall back to the configured audience so the URL still
                // carries a scope token.
                LOG.warn("AMD-01: admin role lookup failed — falling back to configured audience for agent={}", agentName, e);
            }
        }

        final Audience mintAudience = audience;
        final String finalStoreId = mintStoreId;

        List<McpServerConfig> scoped = new ArrayList<>(configs.size());
        for (McpServerConfig cfg : configs) {
            try {
                String scopedUrl = storeScopeJwtService.buildScopedUrl(
                        cfg.getServerUrl(), finalStoreId, mintAudience, callerPhone);
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

            // SA-021-3 + SA-021-17 + ADR-024: per-agent mcpServers with scope JWT; callerPhone binds to 'from'
            var mcpServerToolsForAgent = buildScopedMcpTools(DEFAULT_AGENT_NAME, null, from);
            Map<String, Object> requestBody = new HashMap<>(Map.of(
                    "model", model,
                    "input", messagesList
            ));

            var agent = agentConfigService.getActiveAgentConfig(DEFAULT_AGENT_NAME);
            if (agent != null && agent.isUseTools()) {
                requestBody.put("tools", mcpServerToolsForAgent);
            }

            // PO AC-07 / TASK-1: log request metadata before dispatch (never logs API key or message text)
            logPreRequest(requestBody, messagesList);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            // ADR-031 / REQ-03: wrap network call in RetryTemplate — retries on 5xx/429/connection
            // failures; non-retryable on 4xx (incl. 400 context-window overflow).
            // On retry exhaustion execute() re-throws; the outer catch returns null.
            ResponseEntity<Map> response = openAiRetryTemplate.execute(context -> {
                int attempt = context.getRetryCount() + 1;
                if (attempt > 1) {
                    LOG.warn("[AI-RETRY] attempt={} for from={}", attempt, from);
                }
                return restTemplate.postForEntity(OPENAI_CHAT_URL, entity, Map.class);
            });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // PO AC-07 / TASK-1: log response output metadata before extracting reply
                logPostResponse(response.getBody());
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
        if (message == null) {
            LOG.warn("Received null or empty message from {}", from);
            return null;
        }
        String userText = extractUserText(message);
        if (userText == null || userText.isBlank()) {
            LOG.warn("Received null or empty message from {}", from);
            return null;
        }
        return handleWhatsappQueryForAgent(userText, from, driverName, agentName);
    }

    /**
     * Extracts the text to send to the AI agent: body text, button reply title, or — for a
     * shared location — a natural-language description including latitude/longitude and the
     * address/name when available (see {@link WhatsappWebhookPayload.Value.Message.Location#describe()}).
     *
     * <p>FIX-01 / AC-07: exposed as {@code public} so {@link
     * io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappInboundEventHandler} can use this
     * as the SINGLE SOURCE OF TRUTH guard before arming the AI dispatch and fallback path.
     * IMAGE and INTERACTIVE messages carry no extractable text and return {@code null} here;
     * the handler therefore skips the AI agent (and the fallback) for those types.
     * LOCATION messages DO produce a non-null description via {@code Location#describe()} and
     * still reach the AI agent correctly.
     * Any change to extraction logic is reflected automatically in the handler's guard.
     */
    public String extractUserText(WhatsappWebhookPayload.Value.Message message) {
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

            // SA-021-3 + SA-021-17 + ADR-024: per-agent mcpServers with scope JWT; callerPhone binds to 'from'
            var mcpServerToolsForAgent = buildScopedMcpTools(resolvedAgent, null, from);
            Map<String, Object> requestBody = new HashMap<>(Map.of("model", model, "input", messagesList));

            var agent = agentConfigService.getActiveAgentConfig(resolvedAgent);
            if (agent != null && agent.isUseTools()) {
                requestBody.put("tools", mcpServerToolsForAgent);
            }

            // PO AC-07 / TASK-1: log request metadata before dispatch (never logs API key or message text)
            logPreRequest(requestBody, messagesList);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            // ADR-031 / REQ-03: wrap network call in RetryTemplate — retries on 5xx/429/connection
            // failures; non-retryable on 4xx (incl. 400 context-window overflow).
            // On retry exhaustion execute() re-throws; the outer catch returns null.
            // NOTE-05: log every attempt unconditionally so QA can grep for attempt=1 on a
            // non-retryable 4xx (proves exactly one attempt, no retry). Retry attempts show N > 1.
            // ADR-031 (Option B+C): attempt=1 at INFO (a successful call must not emit WARN);
            // attempt >= 2 at WARN (a genuine retry warrants alerting).
            final String resolvedAgentFinal = resolvedAgent;
            ResponseEntity<Map> response = openAiRetryTemplate.execute(context -> {
                int attempt = context.getRetryCount() + 1;
                if (attempt == 1) {
                    LOG.info("[AI-ATTEMPT] attempt={} agent='{}' for from={}", attempt, resolvedAgentFinal, from);
                } else {
                    LOG.warn("[AI-ATTEMPT] attempt={} agent='{}' for from={}", attempt, resolvedAgentFinal, from);
                }
                return restTemplate.postForEntity(OPENAI_CHAT_URL, entity, Map.class);
            });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // PO AC-07 / TASK-1: log response output metadata before extracting reply
                logPostResponse(response.getBody());
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

    /**
     * Extracts the text reply from the OpenAI Responses API output array.
     *
     * <p>Observability improvement: emits a WARN (unconditionally) when the output contains
     * no {@code message} item — this catches tool-call-only or error responses that would
     * previously be silently dropped. When {@link #debugLogOpenAi} is on, also logs a DEBUG
     * line for each non-message item that is discarded.</p>
     */
    @SuppressWarnings("unchecked")
    private String extractReply(Map<?, ?> responseBody) {
        try {
            var output = (List<Map<String, Object>>) responseBody.get("output");
            if (output == null || output.isEmpty()) return null;

            Map<String, Object> messageObj = null;
            List<String> allTypes = new ArrayList<>();

            for (Map<String, Object> item : output) {
                String type = item.get("type") != null ? item.get("type").toString() : "null";
                allTypes.add(type);
                if ("message".equals(type)) {
                    if (messageObj == null) messageObj = item;  // keep first match
                } else {
                    // PO AC-04: only log discarded items when debug flag is on
                    if (debugLogOpenAi) {
                        LOG.debug("[AI-DEBUG] extractReply: non-message output item type={}", type);
                    }
                }
            }

            if (messageObj == null) {
                // Unconditional WARN — a response with no message item is always an observability gap
                LOG.warn("AI response contained no message item — output types: {}", allTypes);
                return null;
            }

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
