package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistory;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappWebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AiCustomerServiceAgent#handleWhatsappQueryForAgent(WhatsappWebhookPayload.Value.Message, String, String, String)}
 * extracting a descriptive user-text surrogate from an inbound WhatsApp location share
 * (latitude/longitude/address), mirroring how text and button replies already feed the
 * conversation history sent to the AI agent.
 */
@ExtendWith(MockitoExtension.class)
class AiCustomerServiceAgentLocationTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ConversationHistoryService conversationHistoryService;
    @Mock private AiAgentConfigService agentConfigService;
    @Mock private StoreScopeJwtService storeScopeJwtService;

    private static final String FROM = "+27812345678";
    private static final String AGENT_NAME = "customer_support";

    private AiCustomerServiceAgent agent;

    @BeforeEach
    void setUp() {
        agent = new AiCustomerServiceAgent(true, "sk-test-key", "gpt-4.1-mini",
                restTemplate, conversationHistoryService, agentConfigService, storeScopeJwtService);
    }

    @Test
    void handleWhatsappQueryForAgent_locationMessage_sendsLocationDescriptionToAiAgent() {
        when(agentConfigService.getSystemPrompt(AGENT_NAME)).thenReturn("You are a helpful assistant.");
        when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(Collections.emptyList());
        when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(null);

        ConversationHistory conversation = new ConversationHistory();
        conversation.setId("conv-location-1");
        when(conversationHistoryService.getOrCreateConversation(FROM, "Customer", AGENT_NAME))
                .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(
                Map.of("output", List.of(
                        Map.of("type", "message", "content",
                                List.of(Map.of("type", "output_text", "text", "Got it, thanks for sharing your location!")))
                )),
                HttpStatus.OK
        );
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(mockResponse);

        WhatsappWebhookPayload.Value.Message.Location location = new WhatsappWebhookPayload.Value.Message.Location();
        location.setName("Sandton City");
        location.setAddress("83 Rivonia Rd, Sandton");
        location.setLatitude(-26.1076);
        location.setLongitude(28.0526);

        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();
        message.setLocation(location);

        String reply = agent.handleWhatsappQueryForAgent(message, FROM, "Customer", AGENT_NAME);

        assertEquals("Got it, thanks for sharing your location!", reply);
        verify(conversationHistoryService).addUserMessage(
                eq(conversation),
                eq("Customer shared their location: Sandton City, 83 Rivonia Rd, Sandton (latitude: -26.1076, longitude: 28.0526)")
        );
        verify(conversationHistoryService).addAssistantMessage(conversation, "Got it, thanks for sharing your location!");
    }

    @Test
    void handleWhatsappQueryForAgent_messageWithNoTextButtonOrLocation_returnsNull() {
        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();

        String reply = agent.handleWhatsappQueryForAgent(message, FROM, "Customer", AGENT_NAME);

        assertNull(reply);
        verifyNoInteractions(conversationHistoryService);
        verifyNoInteractions(restTemplate);
    }
}
