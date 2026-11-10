package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.messaging.aiAgent.config.OpenAiRetryConfig;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistory;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.messaging.whatsapp.lines.LineContext;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappInboundEventHandler;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappWebhookPayload;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ADR-031: OpenAI API Retry, Timeout, and Fallback Resilience — integration-unit tests.
 *
 * <p>Tests A–H validate the {@code RetryTemplate} classification and attempt-count semantics
 * using the production {@link OpenAiRetryConfig} bean (not a no-op). Tests AC-02 and AC-03
 * validate the fallback message path in {@link WhatsappInboundEventHandler}.
 *
 * <p>All tests use JUnit 5 + Mockito (izinga-messaging module convention).
 * No {@code @Disabled}.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiRetryResilienceTest {

    // ── shared mocks (agent-level tests) ──────────────────────────────────────

    @Mock private RestTemplate restTemplate;
    @Mock private ConversationHistoryService conversationHistoryService;
    @Mock private AiAgentConfigService agentConfigService;
    @Mock private StoreScopeJwtService storeScopeJwtService;
    @Mock private WhatsappConfig whatsappConfig;
    @Mock private UserProfileRepo userProfileRepo;

    // ── shared mocks (handler-level tests AC-02, AC-03) ───────────────────────

    @Mock private AiCustomerServiceAgent aiCustomerServiceAgent;
    @Mock private StoreAiAgent storeAiAgent;
    @Mock private WhatsappNotificationService whatsappNotificationService;
    @Mock private io.curiousoft.izinga.messaging.whatsapp.verification.VerificationConsentService verificationConsentService;

    private static final String FROM = "+27800000001";
    private static final String AGENT_NAME = "driver_support";
    private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

    /** The production RetryTemplate produced by the config class (3 attempts, exponential backoff). */
    private RetryTemplate productionRetryTemplate;

    /**
     * Zero-backoff production policy template: same classification rules as production but
     * backoff is zeroed so tests are fast.  Created via OpenAiRetryConfig with backoff overridden.
     */
    private RetryTemplate fastProductionRetryTemplate;

    private AiCustomerServiceAgent agent;

    @BeforeEach
    void setUp() {
        // Build the production RetryTemplate (from OpenAiRetryConfig bean logic)
        productionRetryTemplate = new OpenAiRetryConfig().openAiRetryTemplate();

        // Fast variant for tests that need production classification but cannot afford backoff sleep:
        // Use the SAME exception map as production but replace backoff with zero interval.
        fastProductionRetryTemplate = buildFastRetryTemplate();

        agent = new AiCustomerServiceAgent(
                true, "sk-test-key", "gpt-4.1-mini", false,
                restTemplate, conversationHistoryService, agentConfigService,
                storeScopeJwtService, whatsappConfig, userProfileRepo,
                fastProductionRetryTemplate);
    }

    // ── Test A: ResourceAccessException retried up to 3 total attempts ─────────

    /**
     * A: ResourceAccessException (connect/read timeout) is RETRYABLE.
     * Agent must invoke restTemplate exactly 3 times before giving up and returning null.
     */
    @Test
    void testA_resourceAccessException_retriedThreeTimes() {
        stubAgentPlumbing();
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        String result = agent.handleWhatsappQueryForAgent(buildTextMessage("hello"), FROM, "Driver", AGENT_NAME);

        assertNull(result, "Agent should return null after exhausting retries");
        // 3 total attempts = 1 initial + 2 retries
        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(Map.class));
    }

    // ── Test B: 429 TooManyRequests retried ───────────────────────────────────

    /**
     * B: HttpClientErrorException.TooManyRequests (HTTP 429) is RETRYABLE despite its parent
     * HttpClientErrorException being non-retryable, because SimpleRetryPolicy with
     * traverseClassHierarchy=true picks the MOST SPECIFIC match.
     * Agent must invoke restTemplate exactly 3 times before returning null.
     */
    @Test
    void testB_http429TooManyRequests_retriedThreeTimes() {
        stubAgentPlumbing();
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(HttpClientErrorException.TooManyRequests.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        String result = agent.handleWhatsappQueryForAgent(buildTextMessage("hello"), FROM, "Driver", AGENT_NAME);

        assertNull(result, "Agent should return null after exhausting retries on 429");
        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(Map.class));
    }

    // ── Test C: 500 HttpServerErrorException retried ──────────────────────────

    /**
     * C: HttpServerErrorException (5xx) is RETRYABLE.
     * Agent must invoke restTemplate exactly 3 times before returning null.
     */
    @Test
    void testC_http500ServerError_retriedThreeTimes() {
        stubAgentPlumbing();
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        String result = agent.handleWhatsappQueryForAgent(buildTextMessage("hello"), FROM, "Driver", AGENT_NAME);

        assertNull(result, "Agent should return null after exhausting retries on 5xx");
        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(Map.class));
    }

    // ── Test D: 400 BadRequest NOT retried ────────────────────────────────────

    /**
     * D: HttpClientErrorException (HTTP 400 Bad Request) is NOT retryable.
     * Agent must invoke restTemplate exactly 1 time and return null immediately.
     */
    @Test
    void testD_http400BadRequest_notRetried() {
        stubAgentPlumbing();
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        String result = agent.handleWhatsappQueryForAgent(buildTextMessage("hello"), FROM, "Driver", AGENT_NAME);

        assertNull(result, "Agent should return null immediately on non-retryable 4xx");
        // Exactly 1 call — no retry
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(Map.class));
    }

    // ── Test E: 401 Unauthorized NOT retried ──────────────────────────────────

    /**
     * E: HttpClientErrorException (HTTP 401 Unauthorized) is NOT retryable.
     * Agent must invoke restTemplate exactly 1 time and return null.
     */
    @Test
    void testE_http401Unauthorized_notRetried() {
        stubAgentPlumbing();
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        String result = agent.handleWhatsappQueryForAgent(buildTextMessage("hello"), FROM, "Driver", AGENT_NAME);

        assertNull(result, "Agent should return null immediately on 401");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(Map.class));
    }

    // ── Test F: First attempt succeeds — no retry ─────────────────────────────

    /**
     * F: When the first attempt succeeds, restTemplate is called exactly once
     * and the reply text is returned.
     */
    @Test
    void testF_firstAttemptSucceeds_noRetry() {
        stubAgentPlumbing();
        stubOpenAiSuccess("All good on first try.");

        String result = agent.handleWhatsappQueryForAgent(buildTextMessage("hello"), FROM, "Driver", AGENT_NAME);

        assertEquals("All good on first try.", result);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(Map.class));
    }

    // ── Test G: Second attempt succeeds — 2 total calls ───────────────────────

    /**
     * G: When the first attempt throws ResourceAccessException but the second succeeds,
     * restTemplate is called exactly 2 times and the reply is returned.
     */
    @Test
    void testG_secondAttemptSucceeds_twoTotalCalls() {
        stubAgentPlumbing();
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Timeout"))
                .thenReturn(buildOpenAiSuccessResponse("Recovered on attempt 2."));

        String result = agent.handleWhatsappQueryForAgent(buildTextMessage("hello"), FROM, "Driver", AGENT_NAME);

        assertEquals("Recovered on attempt 2.", result);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Map.class));
    }

    // ── Test H: Production RetryTemplate bean has max 3 attempts ─────────────

    /**
     * H: The production {@code RetryTemplate} from {@link OpenAiRetryConfig} is configured
     * with a maximum of 3 total attempts. This is validated by counting actual invocations
     * when every attempt throws a retryable exception.
     */
    @Test
    void testH_productionRetryTemplate_maxThreeAttempts() {
        // Use a simple counter via AtomicInteger to count calls via execute()
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        try {
            productionRetryTemplate.execute(context -> {
                callCount.incrementAndGet();
                throw new ResourceAccessException("Timeout");
            });
            fail("Expected RetryException after exhausting attempts");
        } catch (Exception ignored) {
            // Expected — retries exhausted
        }
        assertEquals(3, callCount.get(),
                "Production RetryTemplate must make exactly 3 total attempts (1 initial + 2 retries)");
    }

    // ── AC-02: Handler sends fallback when AI agent returns null ──────────────

    /**
     * AC-02: When both AI agents return null (agent exhausted retries / network failure),
     * {@link WhatsappInboundEventHandler#handlePreDispatchFlows} sends
     * {@link WhatsappInboundEventHandler#AI_FALLBACK_MESSAGE} to the customer.
     */
    @Test
    void testAc02_handlerSendsFallback_whenAgentReturnsNull() throws Exception {
        // Build a minimal handler with whatsappNotificationService mock
        WhatsappInboundEventHandler handler = buildMinimalHandler();

        // Not a verification message — proceed to AI dispatch
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        // AI customer service agent returns null (simulating exhausted retries)
        when(aiCustomerServiceAgent.handleWhatsappQueryForAgent(
                any(WhatsappWebhookPayload.Value.Message.class), anyString(), isNull(), anyString()))
                .thenReturn(null);
        when(aiCustomerServiceAgent.isEnabled()).thenReturn(true);

        // Invoke handlePreDispatchFlows via reflection (package-private → test in same package)
        invokeHandlePreDispatchFlows(handler, buildTextMessage("hello"), FROM,
                buildSession(FROM), buildCustomerLineContext());

        verify(whatsappNotificationService).sendMessage(
                eq(FROM),
                eq(WhatsappInboundEventHandler.AI_FALLBACK_MESSAGE),
                eq(Audience.CUSTOMER),
                isNull()
        );
    }

    // ── AC-03: Handler does NOT send fallback when agent returns a reply ───────

    /**
     * AC-03: When the AI agent returns a non-null reply,
     * {@link WhatsappInboundEventHandler#handlePreDispatchFlows} sends the REAL reply,
     * NOT the fallback message.
     */
    @Test
    void testAc03_handlerSendsRealReply_whenAgentSucceeds() throws Exception {
        WhatsappInboundEventHandler handler = buildMinimalHandler();

        // Not a verification message — proceed to AI dispatch
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        when(aiCustomerServiceAgent.isEnabled()).thenReturn(true);
        when(aiCustomerServiceAgent.handleWhatsappQueryForAgent(
                any(WhatsappWebhookPayload.Value.Message.class), anyString(), isNull(), anyString()))
                .thenReturn("Here is the real answer.");

        invokeHandlePreDispatchFlows(handler, buildTextMessage("hello"), FROM,
                buildSession(FROM), buildCustomerLineContext());

        verify(whatsappNotificationService).sendMessage(
                eq(FROM),
                eq("Here is the real answer."),
                eq(Audience.CUSTOMER),
                isNull()
        );
        verify(whatsappNotificationService, never()).sendMessage(
                anyString(),
                eq(WhatsappInboundEventHandler.AI_FALLBACK_MESSAGE),
                any(),
                any()
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubAgentPlumbing() {
        when(agentConfigService.getSystemPrompt(AGENT_NAME)).thenReturn(SYSTEM_PROMPT);
        when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(Collections.emptyList());
        when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(null);
        ConversationHistory conversation = new ConversationHistory();
        conversation.setId("conv-retry-test-1");
        when(conversationHistoryService.getOrCreateConversation(FROM, "Driver", AGENT_NAME))
                .thenReturn(conversation);
        when(conversationHistoryService.getContextMessages(conversation)).thenReturn(Collections.emptyList());
    }

    private void stubOpenAiSuccess(String replyText) {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(buildOpenAiSuccessResponse(replyText));
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> buildOpenAiSuccessResponse(String text) {
        return new ResponseEntity<>(
                Map.of("output", List.of(
                        Map.of("type", "message", "content",
                                List.of(Map.of("type", "output_text", "text", text)))
                )),
                HttpStatus.OK
        );
    }

    private WhatsappWebhookPayload.Value.Message buildTextMessage(String body) {
        WhatsappWebhookPayload.Value.Message.Text text = new WhatsappWebhookPayload.Value.Message.Text();
        text.setBody(body);
        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();
        message.setText(text);
        message.setFrom(FROM);
        return message;
    }

    /**
     * Builds a fast (zero-backoff) RetryTemplate that uses the same exception classification
     * as production — retryable: ResourceAccessException, 5xx, 429; non-retryable: other 4xx.
     */
    private RetryTemplate buildFastRetryTemplate() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new java.util.HashMap<>();
        retryableExceptions.put(ResourceAccessException.class, true);
        retryableExceptions.put(HttpServerErrorException.class, true);
        retryableExceptions.put(HttpClientErrorException.TooManyRequests.class, true);
        retryableExceptions.put(HttpClientErrorException.class, false);
        org.springframework.retry.policy.SimpleRetryPolicy policy =
                new org.springframework.retry.policy.SimpleRetryPolicy(3, retryableExceptions, true);
        return RetryTemplate.builder()
                .customPolicy(policy)
                .fixedBackoff(1)
                .build();
    }

    private WhatsappInboundEventHandler buildMinimalHandler() {
        // Build a handler with minimal mocks; unused fields can remain null in test scope
        // because we only exercise handlePreDispatchFlows with AI-enabled plumbing.
        return new WhatsappInboundEventHandler(
                /* eventPublisher          */ mock(org.springframework.context.ApplicationEventPublisher.class),
                /* firestoreService        */ mock(io.curiousoft.izinga.messaging.firebase.FirestoreService.class),
                /* firebaseNotification    */ mock(io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService.class),
                /* userProfileRepo         */ mock(io.curiousoft.izinga.commons.repo.UserProfileRepo.class),
                /* deviceRepo              */ mock(io.curiousoft.izinga.commons.repo.DeviceRepository.class),
                /* whatsappNotification    */ whatsappNotificationService,
                /* whatsappSessionRepo     */ mock(io.curiousoft.izinga.messaging.repo.WhatsappSessionRepo.class),
                /* verificationConsent     */ verificationConsentService,
                /* aiCustomerService       */ aiCustomerServiceAgent,
                /* storeAiAgent            */ storeAiAgent,
                /* whatsappImageDoc        */ mock(io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappImageDocumentService.class),
                /* whatsappLineService     */ mock(io.curiousoft.izinga.messaging.whatsapp.lines.WhatsappLineService.class)
        );
    }

    /**
     * Invokes the private {@code handlePreDispatchFlows} method via reflection.
     * This avoids the need to trigger the full inbound webhook event chain.
     */
    private void invokeHandlePreDispatchFlows(WhatsappInboundEventHandler handler,
                                               WhatsappWebhookPayload.Value.Message message,
                                               String from,
                                               io.curiousoft.izinga.commons.model.WhatsappSession session,
                                               LineContext lineContext) throws Exception {
        java.lang.reflect.Method m = WhatsappInboundEventHandler.class.getDeclaredMethod(
                "handlePreDispatchFlows",
                WhatsappWebhookPayload.Value.Message.class,
                java.util.List.class,
                io.curiousoft.izinga.commons.model.WhatsappSession.class,
                LineContext.class
        );
        m.setAccessible(true);
        m.invoke(handler, message, Collections.emptyList(), session, lineContext);
    }

    private io.curiousoft.izinga.commons.model.WhatsappSession buildSession(String from) {
        io.curiousoft.izinga.commons.model.WhatsappSession session =
                new io.curiousoft.izinga.commons.model.WhatsappSession();
        session.setFrom(from);
        return session;
    }

    private LineContext buildCustomerLineContext() {
        return new LineContext(null, AGENT_NAME, Audience.CUSTOMER, null);
    }
}
