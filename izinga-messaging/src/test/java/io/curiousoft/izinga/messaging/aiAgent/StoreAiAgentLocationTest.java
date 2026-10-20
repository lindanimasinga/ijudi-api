package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfig;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link StoreAiAgent}'s private text-extraction path handling an inbound WhatsApp
 * location share for a STORE-audience line, mirroring the existing text/button extraction.
 */
@ExtendWith(MockitoExtension.class)
class StoreAiAgentLocationTest {

    @Mock private AiAgentConfigService agentConfigService;
    @Mock private ConversationHistoryService conversationHistoryService;
    @Mock private StoreContextResolver storeContextResolver;
    @Mock private StoreRepository storeRepository;
    @Mock private StoreScopeJwtService storeScopeJwtService;
    @Mock private RestTemplate restTemplate;

    private static final String STORE_ID = "store-location-test-1";
    private static final String FROM = "+27812345678";

    private StoreAiAgent agent;

    @BeforeEach
    void setUp() {
        agent = new StoreAiAgent(
                true, "sk-test-key", "gpt-4.1-mini", restTemplate,
                agentConfigService, conversationHistoryService,
                storeContextResolver, storeRepository, storeScopeJwtService);
    }

    @Test
    void handleWhatsappQuery_locationMessage_sendsLocationDescriptionAsUserMessage() {
        AiAgentConfig config = AiAgentConfig.builder()
                .agentName("store_support_default")
                .systemPrompt("Base prompt, no store context placeholders here.")
                .active(false)
                .useTools(false)
                .build();

        when(agentConfigService.getActiveAgentConfig("store_support_" + STORE_ID)).thenReturn(null);
        when(agentConfigService.getAgentConfigAnyStatus("store_support_default")).thenReturn(Optional.of(config));
        when(agentConfigService.buildPromptWithCorrections(config)).thenReturn(config.getSystemPrompt());
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        ConversationHistory conversation = new ConversationHistory();
        conversation.setId("conv-store-location-1");
        when(conversationHistoryService.getOrCreateConversation(FROM, "Customer", "store_support_" + STORE_ID))
                .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(
                Map.of("output", List.of(
                        Map.of("type", "message", "content",
                                List.of(Map.of("type", "output_text", "text", "Thanks, noted your location!")))
                )),
                HttpStatus.OK
        );
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(mockResponse);

        WhatsappWebhookPayload.Value.Message.Location location = new WhatsappWebhookPayload.Value.Message.Location();
        location.setLatitude(-29.8587);
        location.setLongitude(31.0218);

        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();
        message.setLocation(location);

        String reply = agent.handleWhatsappQuery(message, FROM, STORE_ID, null);

        assertEquals("Thanks, noted your location!", reply);
        verify(conversationHistoryService).addUserMessage(
                eq(conversation),
                eq("Customer shared their location (latitude: -29.8587, longitude: 31.0218)")
        );
    }

    @Test
    void handleWhatsappQuery_messageWithNoExtractableText_returnsNull() {
        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();

        String reply = agent.handleWhatsappQuery(message, FROM, STORE_ID, null);

        assertNull(reply);
        verifyNoInteractions(conversationHistoryService);
    }
}
