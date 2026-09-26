package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfig;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistory;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappWebhookPayload;
import io.curiousoft.izinga.commons.model.StoreProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FIX-01 regression test: verifies that StoreAiAgent.buildPromptWithStoreContext()
 * resolves ALL four store placeholders through StoreContextResolver/StoreContentSanitizer
 * and that the resulting system prompt:
 *   - does NOT contain the literal "see context" (the old broken path inserted this)
 *   - does NOT contain any unresolved {storeName}/{storeLocation}/{businessHours}/{storeMenu}
 *   - DOES contain the actual context block returned by StoreContextResolver.buildContextBlock()
 *
 * This test MUST FAIL against the pre-fix code (where steps 1-4 consume placeholders before
 * step 5 can replace the delimited block) and MUST PASS after the fix.
 */
@ExtendWith(MockitoExtension.class)
class StoreAiAgentPromptTest {

    @Mock private AiAgentConfigService agentConfigService;
    @Mock private ConversationHistoryService conversationHistoryService;
    @Mock private StoreContextResolver storeContextResolver;
    @Mock private StoreRepository storeRepository;
    @Mock private StoreScopeJwtService storeScopeJwtService;
    @Mock private RestTemplate restTemplate;
    @Mock private StoreProfile storeProfile;

    private StoreAiAgent agent;

    private static final String STORE_ID = "store-prompt-test-123";

    /**
     * The base prompt as stored in AiAgentConfig — contains the four placeholders inside
     * the exact delimited block format used by AiAgentConfigInitializer.
     */
    private static final String BASE_PROMPT =
            "## What This Agent Is\n\n" +
            "Template preamble.\n\n" +
            "## Store Context\n\n" +
            "=== Store Context Begin ===\n" +
            "Store: {storeName}\n" +
            "Location: {storeLocation}\n" +
            "Hours: {businessHours}\n" +
            "Menu:\n" +
            "{storeMenu}\n" +
            "=== Store Context End ===\n\n" +
            "## Suffix instructions.\n";

    /**
     * The resolved context block that StoreContextResolver.buildContextBlock() returns.
     * Represents real store data — sanitized, trimmed (no trailing newline per sanitizeContextBlock).
     */
    private static final String RESOLVED_CONTEXT_BLOCK =
            "Store: Izinga Chicken King\n" +
            "Location: 45 Test Road, Cape Town\n" +
            "Hours:\n" +
            "  MONDAY: 09:00 - 17:00\n" +
            "  TUESDAY: 09:00 - 17:00\n" +
            "Menu:\n" +
            "  - Chicken Burger: Double patty (R75.00)\n" +
            "  - Chips: Crispy golden (R25.00)";

    @BeforeEach
    void setUp() {
        agent = new StoreAiAgent(
                true, "sk-test-key", "gpt-4.1-mini", restTemplate,
                agentConfigService, conversationHistoryService,
                storeContextResolver, storeRepository, storeScopeJwtService);
    }

    /**
     * FIX-01 core assertion:
     * The system prompt sent to OpenAI must contain REAL store data (from StoreContextResolver),
     * NOT the literal "see context" that the broken code produced for businessHours and storeMenu,
     * and NOT any unresolved {placeholder}.
     */
    @Test
    void handleWhatsappQuery_systemPromptContainsRealStoreData_notSeeCcontext() {
        // --- arrange ---
        AiAgentConfig config = AiAgentConfig.builder()
                .agentName("store_support_default")
                .systemPrompt(BASE_PROMPT)
                .active(false)  // template
                .useTools(false) // disable tools to avoid storeScopeJwtService interaction
                .build();

        // Store-specific agent not found → fallback to template
        when(agentConfigService.getActiveAgentConfig("store_support_" + STORE_ID)).thenReturn(null);
        when(agentConfigService.getAgentConfigAnyStatus("store_support_default"))
                .thenReturn(Optional.of(config));
        when(agentConfigService.buildPromptWithCorrections(config)).thenReturn(BASE_PROMPT);

        // Store found in MongoDB
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(storeProfile));
        // StoreContextResolver returns real data (all through StoreContentSanitizer internally)
        when(storeContextResolver.buildContextBlock(storeProfile)).thenReturn(RESOLVED_CONTEXT_BLOCK);

        // Conversation scaffolding
        ConversationHistory conversation = new ConversationHistory();
        conversation.setId("conv-test-01");
        when(conversationHistoryService.getOrCreateConversation(
                eq("+27812345678"), eq("Customer"), eq("store_support_" + STORE_ID)))
                .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation)).thenReturn(List.of());

        // Tools disabled — getMcpToolsForAgent not called; no storeScopeJwtService calls

        // OpenAI response — minimal valid body so extractReply doesn't short-circuit early
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(
                Map.of("output", List.of(
                        Map.of("type", "message", "content",
                                List.of(Map.of("type", "output_text", "text", "Hello!")))
                )),
                HttpStatus.OK
        );

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(anyString(), entityCaptor.capture(), eq(Map.class)))
                .thenReturn(mockResponse);

        // --- act ---
        WhatsappWebhookPayload.Value.Message message = buildTextMessage("What's on the menu?");
        String reply = agent.handleWhatsappQuery(message, "+27812345678", STORE_ID, null);

        // --- assert: reply was produced ---
        assertEquals("Hello!", reply, "agent should return the mocked reply");

        // --- assert: system prompt was correct ---
        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = (Map<String, Object>) entityCaptor.getValue().getBody();
        assertNotNull(requestBody, "OpenAI request body must not be null");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> input = (List<Map<String, Object>>) requestBody.get("input");
        assertNotNull(input, "input list must be present");

        String systemContent = input.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> (String) m.get("content"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No system message found in OpenAI input"));

        // FIX-01: must NOT contain the literal "see context" (old broken output for businessHours/storeMenu)
        assertFalse(systemContent.contains("see context"),
                "System prompt must NOT contain 'see context' — businessHours and storeMenu must be real data, not placeholder text");

        // FIX-01: must NOT contain any unresolved placeholder
        assertFalse(systemContent.contains("{storeName}"),
                "System prompt must not contain unresolved {storeName}");
        assertFalse(systemContent.contains("{storeLocation}"),
                "System prompt must not contain unresolved {storeLocation}");
        assertFalse(systemContent.contains("{businessHours}"),
                "System prompt must not contain unresolved {businessHours}");
        assertFalse(systemContent.contains("{storeMenu}"),
                "System prompt must not contain unresolved {storeMenu}");

        // FIX-01: must contain the actual resolved context from StoreContextResolver
        assertTrue(systemContent.contains(RESOLVED_CONTEXT_BLOCK),
                "System prompt must contain the full resolved context block from StoreContextResolver.\n" +
                "Expected to find:\n" + RESOLVED_CONTEXT_BLOCK + "\n\nActual system prompt:\n" + systemContent);

        // FIX-01: delimiters must be present (block was replaced, not the whole block removed)
        assertTrue(systemContent.contains("=== Store Context Begin ==="),
                "System prompt must contain the Store Context Begin delimiter");
        assertTrue(systemContent.contains("=== Store Context End ==="),
                "System prompt must contain the Store Context End delimiter");
    }

    /**
     * Edge case: if the store is not found in MongoDB, the base prompt is returned unchanged.
     * The unresolved placeholders are acceptable — the store is genuinely unavailable.
     */
    @Test
    void handleWhatsappQuery_storeNotFound_returnsBasePromptUnmodified() {
        AiAgentConfig config = AiAgentConfig.builder()
                .agentName("store_support_default")
                .systemPrompt(BASE_PROMPT)
                .active(false)
                .useTools(false)
                .build();

        when(agentConfigService.getActiveAgentConfig("store_support_" + STORE_ID)).thenReturn(null);
        when(agentConfigService.getAgentConfigAnyStatus("store_support_default"))
                .thenReturn(Optional.of(config));
        when(agentConfigService.buildPromptWithCorrections(config)).thenReturn(BASE_PROMPT);

        // Store NOT found
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        ConversationHistory conversation = new ConversationHistory();
        conversation.setId("conv-no-store");
        when(conversationHistoryService.getOrCreateConversation(
                eq("+27812345678"), eq("Customer"), eq("store_support_" + STORE_ID)))
                .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(
                Map.of("output", List.of(
                        Map.of("type", "message", "content",
                                List.of(Map.of("type", "output_text", "text", "Store unavailable")))
                )),
                HttpStatus.OK
        );

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(anyString(), entityCaptor.capture(), eq(Map.class)))
                .thenReturn(mockResponse);

        WhatsappWebhookPayload.Value.Message message = buildTextMessage("Hello?");
        agent.handleWhatsappQuery(message, "+27812345678", STORE_ID, null);

        // storeContextResolver.buildContextBlock() must NOT be called when store is not found
        verify(storeContextResolver, never()).buildContextBlock(any());

        // Base prompt is returned as-is (unresolved placeholders are expected in this path)
        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = (Map<String, Object>) entityCaptor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> input = (List<Map<String, Object>>) requestBody.get("input");
        String systemContent = input.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> (String) m.get("content"))
                .findFirst()
                .orElseThrow();

        assertEquals(BASE_PROMPT, systemContent,
                "When store is not found, the base prompt must be used unchanged");
    }

    // ---- helpers ----

    private WhatsappWebhookPayload.Value.Message buildTextMessage(String body) {
        WhatsappWebhookPayload.Value.Message msg = new WhatsappWebhookPayload.Value.Message();
        msg.setText(new WhatsappWebhookPayload.Value.Message.Text(body));
        return msg;
    }
}
