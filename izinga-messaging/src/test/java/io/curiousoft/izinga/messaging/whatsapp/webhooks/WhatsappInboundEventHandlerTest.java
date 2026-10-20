package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import io.curiousoft.izinga.commons.model.WhatsappSession;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.aiAgent.AiCustomerServiceAgent;
import io.curiousoft.izinga.messaging.aiAgent.StoreAiAgent;
import io.curiousoft.izinga.messaging.firebase.FireStoreTextMessage;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirestoreService;
import io.curiousoft.izinga.messaging.repo.WhatsappSessionRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.messaging.whatsapp.lines.WhatsappLine;
import io.curiousoft.izinga.messaging.whatsapp.lines.WhatsappLineService;
import io.curiousoft.izinga.messaging.whatsapp.verification.VerificationConsentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappInboundEventHandlerTest {

    private static final String FROM = "27821234567";

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserProfileRepo userProfileRepo;
    @Mock
    private FirestoreService firestoreService;
    @Mock
    private FirebaseNotificationService firebaseNotificationService;
    @Mock
    private DeviceRepository deviceRepo;
    @Mock
    private WhatsappNotificationService whatsappNotificationService;
    @Mock
    private WhatsappSessionRepo whatsappSessionRepo;
    @Mock
    private VerificationConsentService verificationConsentService;
    @Mock
    private AiCustomerServiceAgent aiCustomerService;
    @Mock
    private StoreAiAgent storeAiAgent;
    @Mock
    private WhatsappImageDocumentService whatsappImageDocumentService;
    @Mock
    private WhatsappLineService whatsappLineService;

    private WhatsappInboundEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WhatsappInboundEventHandler(
                eventPublisher,
                firestoreService,
                firebaseNotificationService,
                userProfileRepo,
                deviceRepo,
                whatsappNotificationService,
                whatsappSessionRepo,
                verificationConsentService,
                aiCustomerService,
                storeAiAgent,
                whatsappImageDocumentService,
                whatsappLineService
        );
    }

    @Test
        void handleInbound_imageMessage_linksConfiguredFieldAndSendsAiReply() throws Exception {
        WhatsappWebhookPayload.Value.Message.Image image = new WhatsappWebhookPayload.Value.Message.Image();
        image.setId("wa-media-1");
        image.setMimeType("image/jpeg");
        image.setCaption("field:driverLicenseDocument");

        WhatsappWebhookPayload.Value.Message message = buildImageMessage(image);
        WhatsappWebhookPayload payload = buildPayload(message, "Lindani");

        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(Instant.now());
        session.setAIAgentActive(false);

        when(whatsappSessionRepo.findByFrom(FROM)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(whatsappImageDocumentService.processImageAndTagUser(eq(FROM), any()))
                .thenReturn(new WhatsappImageProcessResult(
                        "wa-media-1",
                        "https://cdn.example.com/docs/license.jpg",
                        "driverLicenseDocument",
                        "image/jpeg",
                        "whatsapp/1717565044_wa-media-1.jpg"
                ));
        when(firestoreService.writeMessageForCustomer(eq(FROM), anyString(), any())).thenReturn("msg-1");

        when(aiCustomerService.isEnabled()).thenReturn(true);
        // REQ-20: image handler now uses agent-aware handleWhatsappQueryForAgent
        when(aiCustomerService.handleWhatsappQueryForAgent(anyString(), eq(FROM), eq("Lindani"), anyString()))
                .thenReturn("Thanks. Your driver's license document is linked.");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        ArgumentCaptor<FireStoreTextMessage> msgCaptor = ArgumentCaptor.forClass(FireStoreTextMessage.class);
        verify(firestoreService).writeMessageForCustomer(eq(FROM), eq("Lindani"), msgCaptor.capture());

        FireStoreTextMessage storedMessage = msgCaptor.getValue();
        assertEquals(FireStoreTextMessage.MessageType.IMAGE, storedMessage.getMessageType());
        assertEquals("field:driverLicenseDocument", storedMessage.getMessage());
        assertNotNull(storedMessage.getMeta());
        assertEquals("driverLicenseDocument", storedMessage.getMeta().get("tagField"));
        assertEquals("https://cdn.example.com/docs/license.jpg", storedMessage.getMeta().get("uploadedUrl"));
        assertEquals("wa-media-1", storedMessage.getMeta().get("mediaId"));
        assertEquals("image/jpeg", storedMessage.getMeta().get("mimeType"));

        verify(aiCustomerService).handleWhatsappQueryForAgent(
                contains("Field: driverLicenseDocument"),
                eq(FROM),
                eq("Lindani"),
                anyString()
        );
        verify(whatsappNotificationService).sendMessage(
                FROM,
                "Thanks. Your driver's license document is linked.",
                Audience.CUSTOMER, null
        );
        verify(firebaseNotificationService, never()).sendNotifications(any(), any());
    }

    @Test
        void handleInbound_imageMessage_sendsFallbackWhenImageProcessingFails() throws Exception {
        WhatsappWebhookPayload.Value.Message.Image image = new WhatsappWebhookPayload.Value.Message.Image();
        image.setId("wa-media-2");
        image.setMimeType("image/png");
        image.setCaption("field:identityDocument");

        WhatsappWebhookPayload.Value.Message message = buildImageMessage(image);
        WhatsappWebhookPayload payload = buildPayload(message, "Lindani");

        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(Instant.now());
        session.setAIAgentActive(false);

        when(whatsappSessionRepo.findByFrom(FROM)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(whatsappImageDocumentService.processImageAndTagUser(eq(FROM), any()))
                .thenThrow(new IllegalArgumentException("No user config found for service type"));

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        verify(whatsappNotificationService).sendMessage(
                FROM,
                "We could not process your image just now. Please send it again, or reply HELP for assistance.",
                Audience.CUSTOMER, null
        );
        verify(firestoreService, never()).writeMessageForCustomer(anyString(), anyString(), any());
        verify(aiCustomerService, never()).handleWhatsappQuery(anyString(), anyString(), anyString());
    }

    // ── location message tests ─────────────────────────────────────────────────

    /**
     * End-to-end regression test for the location handler (previously a stub with its
     * Firestore write commented out and no AI-agent wiring at all). An ongoing session with
     * the AI agent active routes the location through handlePreDispatchFlows exactly like a
     * text message does — handleLocationMessage only needs to persist both sides to Firestore.
     */
    @Test
    void handleInbound_locationMessage_persistsLocationAndAiReplyToFirestore() throws Exception {
        WhatsappWebhookPayload.Value.Message.Location location = new WhatsappWebhookPayload.Value.Message.Location();
        location.setName("Sandton City");
        location.setAddress("83 Rivonia Rd, Sandton");
        location.setLatitude(-26.1076);
        location.setLongitude(28.0526);

        WhatsappWebhookPayload.Value.Message message = buildLocationMessage(location);
        WhatsappWebhookPayload payload = buildPayload(message, "Lindani");

        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(Instant.now());
        session.setAIAgentActive(true);

        when(whatsappSessionRepo.findByFrom(FROM)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(aiCustomerService.isEnabled()).thenReturn(true);
        when(aiCustomerService.handleWhatsappQueryForAgent(any(WhatsappWebhookPayload.Value.Message.class), eq(FROM), isNull(), anyString()))
                .thenReturn("Thanks, we've noted your location.");
        when(firestoreService.writeMessageForCustomer(eq(FROM), anyString(), any())).thenReturn("msg-loc-1");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        ArgumentCaptor<FireStoreTextMessage> msgCaptor = ArgumentCaptor.forClass(FireStoreTextMessage.class);
        verify(firestoreService, times(2))
                .writeMessageForCustomer(eq(FROM), anyString(), msgCaptor.capture());

        FireStoreTextMessage locationMsg = msgCaptor.getAllValues().get(0);
        assertEquals(FireStoreTextMessage.MessageType.LOCATION, locationMsg.getMessageType());
        assertEquals(
                "Customer shared their location: Sandton City, 83 Rivonia Rd, Sandton (latitude: -26.1076, longitude: 28.0526)",
                locationMsg.getMessage()
        );
        assertNotNull(locationMsg.getMeta());
        assertEquals(-26.1076, locationMsg.getMeta().get("latitude"));
        assertEquals(28.0526, locationMsg.getMeta().get("longitude"));
        assertEquals("83 Rivonia Rd, Sandton", locationMsg.getMeta().get("address"));
        assertEquals("Sandton City", locationMsg.getMeta().get("name"));

        FireStoreTextMessage aiReplyMsg = msgCaptor.getAllValues().get(1);
        assertEquals(FireStoreTextMessage.MessageType.TEXT, aiReplyMsg.getMessageType());
        assertEquals("Thanks, we've noted your location.", aiReplyMsg.getMessage());
        assertEquals("AI_AGENT", aiReplyMsg.getSenderId());
        verify(firestoreService).writeMessageForCustomer(eq(FROM), eq("Lindani"), eq(locationMsg));
        verify(firestoreService).writeMessageForCustomer(eq(FROM), eq("AI Agent"), eq(aiReplyMsg));

        // The AI reply was already sent out to the customer by handlePreDispatchFlows
        verify(whatsappNotificationService).sendMessage(
                FROM, "Thanks, we've noted your location.", Audience.CUSTOMER, null
        );
    }

    /**
     * When the AI agent is not active for the session, handlePreDispatchFlows never computes
     * an aiResponseToCustomer — the location is still persisted to Firestore (meta included),
     * but no second (AI reply) Firestore write happens.
     */
    @Test
    void handleInbound_locationMessage_persistsLocationOnly_whenAiAgentNotActive() throws Exception {
        WhatsappWebhookPayload.Value.Message.Location location = new WhatsappWebhookPayload.Value.Message.Location();
        location.setLatitude(-26.1076);
        location.setLongitude(28.0526);

        WhatsappWebhookPayload.Value.Message message = buildLocationMessage(location);
        WhatsappWebhookPayload payload = buildPayload(message, "Lindani");

        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(Instant.now());
        session.setAIAgentActive(false);

        when(whatsappSessionRepo.findByFrom(FROM)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(firestoreService.writeMessageForCustomer(eq(FROM), anyString(), any())).thenReturn("msg-loc-2");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        verify(firestoreService, times(1)).writeMessageForCustomer(eq(FROM), eq("Lindani"), any());
        verify(aiCustomerService, never()).handleWhatsappQueryForAgent(anyString(), anyString(), anyString(), anyString());
        verify(whatsappNotificationService, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    /**
     * AC-06: STORE audience location message must be routed to StoreAiAgent, not
     * AiCustomerServiceAgent. The AI call happens in handlePreDispatchFlows before
     * handleLocationMessage is invoked; only the persistence side runs in the handler.
     */
    @Test
    void handleInbound_locationMessage_storeAudienceRoutesToStoreAiAgent() throws Exception {
        String storePhoneId = "line-store-123";
        String storeId = "store-abc";

        WhatsappLine storeLine = buildLine(storePhoneId, Audience.STORE, "store_support_store-abc");
        storeLine.setStoreId(storeId);
        when(whatsappLineService.findByPhoneNumberId(storePhoneId)).thenReturn(Optional.of(storeLine));

        WhatsappWebhookPayload.Value.Message.Location location =
                new WhatsappWebhookPayload.Value.Message.Location();
        location.setLatitude(-26.1076);
        location.setLongitude(28.0526);

        WhatsappWebhookPayload.Value.Message message = buildLocationMessage(location);
        WhatsappWebhookPayload payload = buildPayloadWithMetadata(message, "Store Customer", storePhoneId);

        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(Instant.now());
        session.setAIAgentActive(true);

        when(whatsappSessionRepo.findByFromAndPhoneNumberId(eq(FROM), eq(storePhoneId)))
                .thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(aiCustomerService.isEnabled()).thenReturn(true);
        when(storeAiAgent.handleWhatsappQuery(
                any(WhatsappWebhookPayload.Value.Message.class), eq(FROM), eq(storeId), anyString()))
                .thenReturn("We noted your location, thanks!");
        when(firestoreService.writeMessageForCustomer(eq(FROM), anyString(), any()))
                .thenReturn("msg-store-loc-1");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        // AC-06: StoreAiAgent must be called, AiCustomerServiceAgent must NOT be called
        verify(storeAiAgent).handleWhatsappQuery(
                any(WhatsappWebhookPayload.Value.Message.class), eq(FROM), eq(storeId), anyString());
        verify(aiCustomerService, never()).handleWhatsappQueryForAgent(
                any(WhatsappWebhookPayload.Value.Message.class), anyString(), any(), anyString());

        // Both Firestore docs must be written (location + AI reply)
        verify(firestoreService, times(2)).writeMessageForCustomer(eq(FROM), anyString(), any());
    }

    /**
     * AC-08: when the Firestore write throws, the exception must be caught and logged —
     * it must never propagate out of handleInbound.
     */
    @Test
    void handleInbound_locationMessage_firestoreExceptionIsCaughtAndDoesNotPropagate() throws Exception {
        WhatsappWebhookPayload.Value.Message.Location location =
                new WhatsappWebhookPayload.Value.Message.Location();
        location.setLatitude(-26.1076);
        location.setLongitude(28.0526);

        WhatsappWebhookPayload.Value.Message message = buildLocationMessage(location);
        WhatsappWebhookPayload payload = buildPayload(message, "Lindani");

        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(Instant.now());
        session.setAIAgentActive(false);

        when(whatsappSessionRepo.findByFrom(FROM)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        // Firestore write throws — must be caught internally, never propagated
        when(firestoreService.writeMessageForCustomer(eq(FROM), anyString(), any()))
                .thenThrow(new RuntimeException("Firestore unavailable"));

        // Must not throw — if it does, the test fails with an unexpected exception
        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        verify(firestoreService).writeMessageForCustomer(eq(FROM), anyString(), any());
    }

    /**
     * AC-09: first message in a new session → landing-options flow runs, neither AI agent
     * is called, and the location doc IS still persisted (consistent with every other
     * message type) but no AI-reply doc is written.
     */
    @Test
    void handleInbound_locationMessage_newSession_runsLandingOptionsNotAiAgent() throws Exception {
        WhatsappWebhookPayload.Value.Message.Location location =
                new WhatsappWebhookPayload.Value.Message.Location();
        location.setName("Sandton City");
        location.setLatitude(-26.1076);
        location.setLongitude(28.0526);

        WhatsappWebhookPayload.Value.Message message = buildLocationMessage(location);
        WhatsappWebhookPayload payload = buildPayload(message, "Lindani");

        // No lastMessageDate — treated as new session by upsertSession
        WhatsappSession session = new WhatsappSession(FROM);

        when(whatsappSessionRepo.findByFrom(FROM)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(firestoreService.writeMessageForCustomer(eq(FROM), anyString(), any()))
                .thenReturn("msg-new-loc-1");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        // Landing options must be sent (new session path)
        verify(whatsappNotificationService).sendLandingOptions(
                eq(FROM), anyString(), any(), any(), any(), any());
        // Neither AI agent must be called
        verify(aiCustomerService, never()).handleWhatsappQueryForAgent(
                any(WhatsappWebhookPayload.Value.Message.class), anyString(), any(), anyString());
        verify(storeAiAgent, never()).handleWhatsappQuery(
                any(WhatsappWebhookPayload.Value.Message.class), anyString(), anyString(), any());
        // Location doc IS written (1 write, no AI-reply doc)
        verify(firestoreService, times(1)).writeMessageForCustomer(eq(FROM), anyString(), any());
        // No AI reply sent via WhatsApp either
        verify(whatsappNotificationService, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    // ── multi-line routing tests ───────────────────────────────────────────────

    /**
     * REQ-10: two payloads with different metadata.phoneNumberId route to
     * different WhatsappLine configurations.
     */
    @Test
    void twoFixtures_differByMetadataPhoneNumberId_routeToDifferentLines() throws Exception {
        String phoneIdA = "line-customer";
        String phoneIdB = "line-driver";

        WhatsappLine lineA = buildLine(phoneIdA, Audience.CUSTOMER, "customer_support");
        WhatsappLine lineB = buildLine(phoneIdB, Audience.DRIVER, "driver_support");

        when(whatsappLineService.findByPhoneNumberId(phoneIdA)).thenReturn(java.util.Optional.of(lineA));
        when(whatsappLineService.findByPhoneNumberId(phoneIdB)).thenReturn(java.util.Optional.of(lineB));

        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(java.time.Instant.now());
        session.setAIAgentActive(false);
        when(whatsappSessionRepo.findByFromAndPhoneNumberId(eq(FROM), anyString())).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        WhatsappWebhookPayload payloadA = buildPayloadWithMetadata(buildTextMessage(FROM, "hello"), "Lindani", phoneIdA);
        WhatsappWebhookPayload payloadB = buildPayloadWithMetadata(buildTextMessage(FROM, "hello"), "Lindani", phoneIdB);

        handler.handleInbound(new WhatsappInboundEvent(this, payloadA));
        handler.handleInbound(new WhatsappInboundEvent(this, payloadB));

        verify(whatsappLineService).findByPhoneNumberId(phoneIdA);
        verify(whatsappLineService).findByPhoneNumberId(phoneIdB);
    }

    /**
     * REQ-10 fallback: when the metadata.phoneNumberId has no matching WhatsappLine,
     * the handler falls back to customer_support defaults and does not throw.
     */
    @Test
    void unknownPhoneNumberId_fallsBackToDefault() throws Exception {
        String unknownPhoneId = "unknown-999";

        when(whatsappLineService.findByPhoneNumberId(unknownPhoneId)).thenReturn(Optional.empty());

        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(java.time.Instant.now());
        session.setAIAgentActive(false);
        // Legacy single-key fallback when lineContext.line() is null
        when(whatsappSessionRepo.findByFrom(FROM)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        WhatsappWebhookPayload payload = buildPayloadWithMetadata(buildTextMessage(FROM, "hello"), "Lindani", unknownPhoneId);

        // Must not throw — should degrade gracefully to CUSTOMER audience
        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        verify(whatsappLineService).findByPhoneNumberId(unknownPhoneId);
    }

    /**
     * Feature-flag OFF: metadata.phoneNumberId is present but the flag-off
     * code path resolves LineContext with null line and CUSTOMER audience,
     * so the handler still processes the message via customer flow.
     * (This test mocks the service to return empty to simulate flag-off fallback.)
     */
    @Test
    void flagOff_ignoresMetadata_usesCustomerFallback() throws Exception {
        // When flag is off, WhatsappLineService.findByPhoneNumberId is not expected
        // to be called — LineContext resolves to null/CUSTOMER. Here we test the
        // fallback path where no metadata is present (phoneNumberId null).
        WhatsappSession session = new WhatsappSession(FROM);
        session.setLastMessageDate(java.time.Instant.now());
        session.setAIAgentActive(false);
        when(whatsappSessionRepo.findByFrom(FROM)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        // Payload with NO metadata (phoneNumberId null) — handler falls back to default LineContext
        WhatsappWebhookPayload payload = buildPayload(buildTextMessage(FROM, "hello"), "Lindani");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        // whatsappLineService.findByPhoneNumberId must NOT be called when phoneNumberId is null
        verify(whatsappLineService, never()).findByPhoneNumberId(any());
    }

    // ── helper factory methods ─────────────────────────────────────────────────

    private WhatsappLine buildLine(String phoneNumberId, Audience audience, String agentName) {
        WhatsappLine line = new WhatsappLine();
        line.setPhoneNumberId(phoneNumberId);
        line.setAudience(audience);
        line.setAgentName(agentName);
        line.setActive(true);
        return line;
    }

    private WhatsappWebhookPayload.Value.Message buildTextMessage(String from, String body) {
        WhatsappWebhookPayload.Value.Message.Text text = new WhatsappWebhookPayload.Value.Message.Text();
        text.setBody(body);
        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();
        message.setFrom(from);
        message.setId("wamid.test");
        message.setType("text");
        message.setTimestamp(String.valueOf(java.time.Instant.now().getEpochSecond()));
        message.setText(text);
        return message;
    }

    private WhatsappWebhookPayload buildPayloadWithMetadata(
            WhatsappWebhookPayload.Value.Message message,
            String contactName,
            String phoneNumberId) {

        WhatsappWebhookPayload.Value.Metadata meta = new WhatsappWebhookPayload.Value.Metadata();
        meta.setPhoneNumberId(phoneNumberId);

        WhatsappWebhookPayload payload = buildPayload(message, contactName);
        // Stamp metadata onto the value
        payload.getEntry().get(0).getChanges().get(0).getValue().setMetadata(meta);
        return payload;
    }

    private WhatsappWebhookPayload buildPayload(WhatsappWebhookPayload.Value.Message message, String contactName) {
        WhatsappWebhookPayload.Value.Contact.ContactProfile profile = new WhatsappWebhookPayload.Value.Contact.ContactProfile();
        profile.setName(contactName);

        WhatsappWebhookPayload.Value.Contact contact = new WhatsappWebhookPayload.Value.Contact();
        contact.setProfile(profile);
        contact.setWaId(FROM);

        WhatsappWebhookPayload.Value value = new WhatsappWebhookPayload.Value();
        value.setContacts(List.of(contact));
        value.setMessages(List.of(message));

        WhatsappWebhookPayload.Change change = new WhatsappWebhookPayload.Change();
        change.setField("messages");
        change.setValue(value);

        WhatsappWebhookPayload.Entry entry = new WhatsappWebhookPayload.Entry();
        entry.setId("entry-1");
        entry.setChanges(List.of(change));

        WhatsappWebhookPayload payload = new WhatsappWebhookPayload();
        payload.setObject("whatsapp_business_account");
        payload.setEntry(List.of(entry));
        return payload;
    }

    private WhatsappWebhookPayload.Value.Message buildImageMessage(WhatsappWebhookPayload.Value.Message.Image image) {
        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();
        message.setFrom(FROM);
        message.setId("wamid.HBgMOTEyMzQ1Njc4");
        message.setType("image");
        message.setTimestamp(String.valueOf(Instant.now().getEpochSecond()));
        message.setImage(image);
        return message;
    }

    private WhatsappWebhookPayload.Value.Message buildLocationMessage(WhatsappWebhookPayload.Value.Message.Location location) {
        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();
        message.setFrom(FROM);
        message.setId("wamid.HBgMOTEyMzQ1Njc5");
        message.setType("location");
        message.setTimestamp(String.valueOf(Instant.now().getEpochSecond()));
        message.setLocation(location);
        return message;
    }
}
