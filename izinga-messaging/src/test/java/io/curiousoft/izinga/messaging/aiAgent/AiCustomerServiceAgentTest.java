package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistory;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappWebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Disabled
@ExtendWith(MockitoExtension.class)
class AiCustomerServiceAgentTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ConversationHistoryService conversationHistoryService;

    @Mock
    private AiAgentConfigService agentConfigService;

    private static final String FAKE_API_KEY = "sk-test-key";
    private static final String FAKE_MODEL = "gpt-4.1-mini";
    private static final String FROM = "+27812345678";
    private static final String DRIVER_NAME = "John";
    private static final String SYSTEM_PROMPT = "# Customer Service Agent for Drivers";

    private AiCustomerServiceAgent enabledAgent;
    private AiCustomerServiceAgent disabledAgent;

    @BeforeEach
    void setUp() {
        enabledAgent = new AiCustomerServiceAgent(true, FAKE_API_KEY, FAKE_MODEL, restTemplate, conversationHistoryService, agentConfigService);
        disabledAgent = new AiCustomerServiceAgent(false, FAKE_API_KEY, FAKE_MODEL, restTemplate, conversationHistoryService, agentConfigService);
    }

    // ─── isEnabled ────────────────────────────────────────────────────────────

    @Test
    void isEnabled_returnsTrue_whenAgentIsEnabled() {
        assertTrue(enabledAgent.isEnabled());
    }

    @Test
    void isEnabled_returnsFalse_whenAgentIsDisabled() {
        assertFalse(disabledAgent.isEnabled());
    }

    // ─── handleWhatsappQuery — disabled agent ──────────────────────────────────

    @Test
    void handleWhatsappQuery_returnsNull_whenAgentIsDisabled() {
        var message = buildTextMessage("How do I register?");
        var result = disabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        assertNull(result);
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(conversationHistoryService);
    }

    // ─── handleWhatsappQuery — null / blank guards ─────────────────────────────

    @Test
    void handleWhatsappQuery_returnsNull_whenMessageIsNull() {
        var result = enabledAgent.handleWhatsappQuery(null, FROM, DRIVER_NAME);

        assertNull(result);
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(conversationHistoryService);
    }

    @Test
    void handleWhatsappQuery_returnsNull_whenTextIsNull() {
        var message = new WhatsappWebhookPayload.Value.Message();
        message.setText(null);

        var result = enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        assertNull(result);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void handleWhatsappQuery_returnsNull_whenBodyIsBlank() {
        var message = buildTextMessage("   ");

        var result = enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        assertNull(result);
        verifyNoInteractions(restTemplate);
    }

    // ─── handleWhatsappQuery — conversation history integration ────────────────

    @Test
    void handleWhatsappQuery_createsOrRetrievesConversation() {
        var message = buildTextMessage("How do payouts work?");
        var conversation = buildConversation();

        when(agentConfigService.getSystemPrompt("driver_support"))
            .thenReturn(SYSTEM_PROMPT);
        when(conversationHistoryService.getOrCreateConversation(FROM, DRIVER_NAME))
            .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation))
            .thenReturn(new ArrayList<>());
        stubOpenAiResponse("iZinga supports daily payouts.");

        enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        verify(conversationHistoryService, times(1)).getOrCreateConversation(FROM, DRIVER_NAME);
    }

    @Test
    void handleWhatsappQuery_addsUserMessageToHistory() {
        var message = buildTextMessage("What is the daily limit?");
        var conversation = buildConversation();

        when(agentConfigService.getSystemPrompt("driver_support"))
            .thenReturn(SYSTEM_PROMPT);
        when(conversationHistoryService.getOrCreateConversation(FROM, DRIVER_NAME))
            .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation))
            .thenReturn(new ArrayList<>());
        stubOpenAiResponse("The daily limit is R3000.");

        enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        verify(conversationHistoryService, times(1)).addUserMessage(eq(conversation), eq("What is the daily limit?"));
    }

    @Test
    void handleWhatsappQuery_addAssistantMessageToHistory_onSuccess() {
        var message = buildTextMessage("How do I get approved?");
        var conversation = buildConversation();
        var reply = "Your profile goes through review...";

        when(agentConfigService.getSystemPrompt("driver_support"))
            .thenReturn(SYSTEM_PROMPT);
        when(conversationHistoryService.getOrCreateConversation(FROM, DRIVER_NAME))
            .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation))
            .thenReturn(new ArrayList<>());
        stubOpenAiResponse(reply);

        var result = enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        assertEquals(reply, result);
        verify(conversationHistoryService, times(1)).addAssistantMessage(eq(conversation), eq(reply));
    }

    @Test
    void handleWhatsappQuery_returnsAiReply_whenOpenAiSucceeds() {
        var message = buildTextMessage("How do I register as a driver?");
        var conversation = buildConversation();

        when(agentConfigService.getSystemPrompt("driver_support"))
            .thenReturn(SYSTEM_PROMPT);
        when(conversationHistoryService.getOrCreateConversation(FROM, DRIVER_NAME))
            .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation))
            .thenReturn(new ArrayList<>());
        stubOpenAiResponse("To register, start by verifying your phone number...");

        var result = enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        assertNotNull(result);
        assertEquals("To register, start by verifying your phone number...", result);
    }

    @Test
    void handleWhatsappQuery_supportLegacySignature_withoutDriverName() {
        var message = buildTextMessage("How do payouts work?");
        var conversation = buildConversation();

        when(agentConfigService.getSystemPrompt("driver_support"))
            .thenReturn(SYSTEM_PROMPT);
        when(conversationHistoryService.getOrCreateConversation(FROM, "Driver"))
            .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation))
            .thenReturn(new ArrayList<>());
        stubOpenAiResponse("Payouts happen daily.");

        var result = enabledAgent.handleWhatsappQuery(message, FROM);

        assertNotNull(result);
        verify(conversationHistoryService).getOrCreateConversation(FROM, "Driver");
    }

    @Test
    void handleWhatsappQuery_returnsNull_whenOpenAiThrowsException() {
        var message = buildTextMessage("How do I get approved?");
        var conversation = buildConversation();

        when(agentConfigService.getSystemPrompt("driver_support"))
            .thenReturn(SYSTEM_PROMPT);
        when(conversationHistoryService.getOrCreateConversation(FROM, DRIVER_NAME))
            .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation))
            .thenReturn(new ArrayList<>());
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new RuntimeException("Network error"));

        var result = enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        assertNull(result);
    }

    @Test
    void handleWhatsappQuery_returnsNull_whenOpenAiReturnsNon2xx() {
        var message = buildTextMessage("When will I start working?");
        var conversation = buildConversation();

        when(agentConfigService.getSystemPrompt("driver_support"))
            .thenReturn(SYSTEM_PROMPT);
        when(conversationHistoryService.getOrCreateConversation(FROM, DRIVER_NAME))
            .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation))
            .thenReturn(new ArrayList<>());
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

        var result = enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        assertNull(result);
    }

    @Test
    void handleWhatsappQuery_returnsNull_whenSystemPromptNotFound() {
        var message = buildTextMessage("How do I register?");

        when(agentConfigService.getSystemPrompt("driver_support"))
            .thenReturn(null);

        var result = enabledAgent.handleWhatsappQuery(message, FROM, DRIVER_NAME);

        assertNull(result);
        verify(conversationHistoryService, never()).getOrCreateConversation(anyString(), anyString());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private WhatsappWebhookPayload.Value.Message buildTextMessage(String body) {
        var message = new WhatsappWebhookPayload.Value.Message();
        var text = new WhatsappWebhookPayload.Value.Message.Text();
        text.setBody(body);
        message.setText(text);
        return message;
    }

    private ConversationHistory buildConversation() {
        return ConversationHistory.builder()
            .id("1")
            .driverPhoneNumber(FROM)
            .driverName(DRIVER_NAME)
            .messages(new ArrayList<>())
            .createdAt(Instant.now())
            .lastMessageAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .archived(false)
            .build();
    }

    private void stubOpenAiResponse(String replyContent) {
        var messageMap = Map.of("role", "assistant", "content", replyContent);
        var choice = Map.of("message", messageMap, "index", 0, "finish_reason", "stop");
        var responseBody = Map.of("choices", List.of(choice));
        when(restTemplate.postForEntity(
                eq("https://api.openai.com/v1/chat/completions"),
                any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(responseBody));
    }
}

