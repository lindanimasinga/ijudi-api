package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import io.curiousoft.izinga.commons.model.WhatsappSession;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.aiAgent.AiCustomerServiceAgent;
import io.curiousoft.izinga.messaging.aiAgent.StoreAiAgent;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T-17: Store line webhook integration test.
 *
 * Tests the end-to-end routing path from a WhatsApp webhook payload through
 * WhatsappInboundEventHandler to the correct AI agent, and verifies that:
 *
 * 1. STORE audience line → StoreAiAgent is called, not AiCustomerServiceAgent
 * 2. CUSTOMER audience line → AiCustomerServiceAgent is called, not StoreAiAgent
 * 3. WhatsappSession created from a STORE line carries storeId=X (AC-13)
 * 4. WhatsappSession created from a CUSTOMER line carries null storeId
 * 5. Two-entry fixture: distinct sessions are created for STORE and CUSTOMER lines
 * 6. DRIVER line does NOT receive customer landing options (SA-5)
 */
@ExtendWith(MockitoExtension.class)
class StoreLineWebhookIntegrationTest {

    private static final String FROM_CUSTOMER = "27820000001";
    private static final String FROM_STORE    = "27820000002";
    private static final String STORE_ID      = "store-x-fixture";
    private static final String PHONE_ID_STORE    = "wa-phone-store-001";
    private static final String PHONE_ID_CUSTOMER = "wa-phone-customer-001";
    private static final String AGENT_NAME_STORE  = "store_support_" + STORE_ID;

    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private FirestoreService firestoreService;
    @Mock private FirebaseNotificationService firebaseNotificationService;
    @Mock private DeviceRepository deviceRepo;
    @Mock private WhatsappNotificationService whatsappNotificationService;
    @Mock private WhatsappSessionRepo whatsappSessionRepo;
    @Mock private VerificationConsentService verificationConsentService;
    @Mock private AiCustomerServiceAgent aiCustomerService;
    @Mock private StoreAiAgent storeAiAgent;
    @Mock private WhatsappImageDocumentService whatsappImageDocumentService;
    @Mock private WhatsappLineService whatsappLineService;

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

    // ---- helper factories ----

    private WhatsappLine storeLine() {
        return storeLine(null);
    }

    private WhatsappLine storeLine(String landingTemplateName) {
        WhatsappLine line = new WhatsappLine();
        line.setPhoneNumberId(PHONE_ID_STORE);
        line.setAudience(Audience.STORE);
        line.setAgentName(AGENT_NAME_STORE);
        line.setStoreId(STORE_ID);
        line.setActive(true);
        line.setLandingTemplateName(landingTemplateName);
        return line;
    }

    private WhatsappLine customerLine() {
        WhatsappLine line = new WhatsappLine();
        line.setPhoneNumberId(PHONE_ID_CUSTOMER);
        line.setAudience(Audience.CUSTOMER);
        line.setAgentName("customer_support");
        line.setStoreId(null);
        line.setActive(true);
        return line;
    }

    private WhatsappWebhookPayload buildTextPayload(String from, String body,
                                                    String phoneNumberId, String contactName) {
        WhatsappWebhookPayload.Value.Message.Text text = new WhatsappWebhookPayload.Value.Message.Text();
        text.setBody(body);

        WhatsappWebhookPayload.Value.Message message = new WhatsappWebhookPayload.Value.Message();
        message.setFrom(from);
        message.setId("wamid.t17-" + phoneNumberId);
        message.setType("text");
        message.setTimestamp(String.valueOf(Instant.now().getEpochSecond()));
        message.setText(text);

        WhatsappWebhookPayload.Value.Contact.ContactProfile profile =
                new WhatsappWebhookPayload.Value.Contact.ContactProfile();
        profile.setName(contactName);

        WhatsappWebhookPayload.Value.Contact contact = new WhatsappWebhookPayload.Value.Contact();
        contact.setProfile(profile);
        contact.setWaId(from);

        WhatsappWebhookPayload.Value.Metadata meta = new WhatsappWebhookPayload.Value.Metadata();
        meta.setPhoneNumberId(phoneNumberId);

        WhatsappWebhookPayload.Value value = new WhatsappWebhookPayload.Value();
        value.setContacts(List.of(contact));
        value.setMessages(List.of(message));
        value.setMetadata(meta);

        WhatsappWebhookPayload.Change change = new WhatsappWebhookPayload.Change();
        change.setField("messages");
        change.setValue(value);

        WhatsappWebhookPayload.Entry entry = new WhatsappWebhookPayload.Entry();
        entry.setId("entry-t17");
        entry.setChanges(List.of(change));

        WhatsappWebhookPayload payload = new WhatsappWebhookPayload();
        payload.setObject("whatsapp_business_account");
        payload.setEntry(List.of(entry));
        return payload;
    }

    private WhatsappSession existingSession(String from, String phoneNumberId, boolean isAiActive) {
        WhatsappSession session = new WhatsappSession(from);
        session.setPhoneNumberId(phoneNumberId);
        session.setLastMessageDate(Instant.now().minusSeconds(60)); // not new session
        session.setAIAgentActive(isAiActive);
        return session;
    }

    // ---- T-17 test 1: STORE audience line → StoreAiAgent called, not AiCustomerServiceAgent ----

    @Test
    void storeLine_textMessage_routedToStoreAiAgent_notCustomerAgent() {
        when(whatsappLineService.findByPhoneNumberId(PHONE_ID_STORE))
                .thenReturn(Optional.of(storeLine()));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(aiCustomerService.isEnabled()).thenReturn(true);

        WhatsappSession session = existingSession(FROM_STORE, PHONE_ID_STORE, true);
        when(whatsappSessionRepo.findByFromAndPhoneNumberId(FROM_STORE, PHONE_ID_STORE))
                .thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(storeAiAgent.handleWhatsappQuery(any(), eq(FROM_STORE), eq(STORE_ID), eq(AGENT_NAME_STORE)))
                .thenReturn("Welcome to " + STORE_ID + "! How can I help?");

        WhatsappWebhookPayload payload = buildTextPayload(
                FROM_STORE, "What do you have on the menu?", PHONE_ID_STORE, "Store Customer");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        // StoreAiAgent MUST be called for STORE audience
        verify(storeAiAgent).handleWhatsappQuery(
                any(), eq(FROM_STORE), eq(STORE_ID), eq(AGENT_NAME_STORE));

        // AiCustomerServiceAgent must NOT handle this message — verify the two concrete overloads
        verify(aiCustomerService, never()).handleWhatsappQuery(
                any(WhatsappWebhookPayload.Value.Message.class), any(String.class), any());
        verify(aiCustomerService, never()).handleWhatsappQueryForAgent(
                any(WhatsappWebhookPayload.Value.Message.class), any(String.class), any(), any());
    }

    // ---- T-17 test 2: CUSTOMER audience line → AiCustomerServiceAgent called, not StoreAiAgent ----

    @Test
    void customerLine_textMessage_routedToCustomerAgent_notStoreAgent() {
        when(whatsappLineService.findByPhoneNumberId(PHONE_ID_CUSTOMER))
                .thenReturn(Optional.of(customerLine()));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(aiCustomerService.isEnabled()).thenReturn(true);

        WhatsappSession session = existingSession(FROM_CUSTOMER, PHONE_ID_CUSTOMER, true);
        when(whatsappSessionRepo.findByFromAndPhoneNumberId(FROM_CUSTOMER, PHONE_ID_CUSTOMER))
                .thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(aiCustomerService.handleWhatsappQueryForAgent(
                any(WhatsappWebhookPayload.Value.Message.class),
                eq(FROM_CUSTOMER), any(), eq("customer_support")))
                .thenReturn("Hi, how can I help you?");

        WhatsappWebhookPayload payload = buildTextPayload(
                FROM_CUSTOMER, "Where is my order?", PHONE_ID_CUSTOMER, "Customer A");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        // AiCustomerServiceAgent MUST be called for CUSTOMER audience
        verify(aiCustomerService).handleWhatsappQueryForAgent(
                any(WhatsappWebhookPayload.Value.Message.class),
                eq(FROM_CUSTOMER), any(), eq("customer_support"));

        // StoreAiAgent must NOT be called for a CUSTOMER audience line
        verify(storeAiAgent, never()).handleWhatsappQuery(any(), any(), any(), any());
    }

    // ---- T-17 test 3: Session created for STORE line carries storeId=X (AC-13) ----

    @Test
    void storeLine_newSession_sessionSavedWithStoreId() {
        when(whatsappLineService.findByPhoneNumberId(PHONE_ID_STORE))
                .thenReturn(Optional.of(storeLine()));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        // No existing session → new session will be created
        when(whatsappSessionRepo.findByFromAndPhoneNumberId(FROM_STORE, PHONE_ID_STORE))
                .thenReturn(Optional.empty());
        when(whatsappSessionRepo.findByFrom(FROM_STORE)).thenReturn(Optional.empty());

        ArgumentCaptor<WhatsappSession> sessionCaptor = ArgumentCaptor.forClass(WhatsappSession.class);
        when(whatsappSessionRepo.save(sessionCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        // New sessions trigger landing options unless DRIVER — mock user lookup to avoid NPE
        when(userProfileRepo.findByMobileNumber(FROM_STORE)).thenReturn(null);
        when(userProfileRepo.findByRole(any())).thenReturn(List.of());
        when(deviceRepo.findByUserIdIn(any())).thenReturn(List.of());

        WhatsappWebhookPayload payload = buildTextPayload(
                FROM_STORE, "Hello", PHONE_ID_STORE, "New Store Customer");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        // Find the session that was saved (the first save call before AI routing)
        List<WhatsappSession> savedSessions = sessionCaptor.getAllValues();
        assertFalse(savedSessions.isEmpty(), "at least one session must be saved");

        WhatsappSession saved = savedSessions.get(0);
        // AC-13: session must carry storeId from the STORE line
        assertEquals(STORE_ID, saved.getStoreId(),
                "session saved for STORE line must carry storeId=" + STORE_ID);
        assertEquals(PHONE_ID_STORE, saved.getPhoneNumberId(),
                "session must carry phoneNumberId of the STORE line");
        assertEquals(AGENT_NAME_STORE, saved.getAgentName(),
                "session must carry agentName of the STORE line");

        // REQ-24: no landingTemplateName configured on this line → falls back to the
        // generic template (null sixth arg; WhatsappNotificationService defaults it).
        verify(whatsappNotificationService).sendLandingOptions(
                eq(FROM_STORE), eq("New Store Customer"), any(),
                eq(Audience.STORE), eq(STORE_ID), isNull());
    }

    // ---- REQ-24: STORE line with its own landingTemplateName uses that template, not the generic one ----

    @Test
    void storeLine_withLandingTemplateName_threadsTemplateNameIntoSendLandingOptions() {
        when(whatsappLineService.findByPhoneNumberId(PHONE_ID_STORE))
                .thenReturn(Optional.of(storeLine("rxnova24_landing_options")));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        when(whatsappSessionRepo.findByFromAndPhoneNumberId(FROM_STORE, PHONE_ID_STORE))
                .thenReturn(Optional.empty());
        when(whatsappSessionRepo.findByFrom(FROM_STORE)).thenReturn(Optional.empty());
        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(userProfileRepo.findByMobileNumber(FROM_STORE)).thenReturn(null);
        when(userProfileRepo.findByRole(any())).thenReturn(List.of());
        when(deviceRepo.findByUserIdIn(any())).thenReturn(List.of());

        WhatsappWebhookPayload payload = buildTextPayload(
                FROM_STORE, "Hello", PHONE_ID_STORE, "New Store Customer");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        verify(whatsappNotificationService).sendLandingOptions(
                eq(FROM_STORE), eq("New Store Customer"), any(),
                eq(Audience.STORE), eq(STORE_ID), eq("rxnova24_landing_options"));
    }

    // ---- T-17 test 4: Session created for CUSTOMER line carries null storeId ----

    @Test
    void customerLine_newSession_sessionSavedWithNullStoreId() {
        when(whatsappLineService.findByPhoneNumberId(PHONE_ID_CUSTOMER))
                .thenReturn(Optional.of(customerLine()));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        when(whatsappSessionRepo.findByFromAndPhoneNumberId(FROM_CUSTOMER, PHONE_ID_CUSTOMER))
                .thenReturn(Optional.empty());
        when(whatsappSessionRepo.findByFrom(FROM_CUSTOMER)).thenReturn(Optional.empty());

        ArgumentCaptor<WhatsappSession> sessionCaptor = ArgumentCaptor.forClass(WhatsappSession.class);
        when(whatsappSessionRepo.save(sessionCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(userProfileRepo.findByMobileNumber(FROM_CUSTOMER)).thenReturn(null);
        when(userProfileRepo.findByRole(any())).thenReturn(List.of());
        when(deviceRepo.findByUserIdIn(any())).thenReturn(List.of());

        WhatsappWebhookPayload payload = buildTextPayload(
                FROM_CUSTOMER, "Hello", PHONE_ID_CUSTOMER, "New Customer");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        List<WhatsappSession> savedSessions = sessionCaptor.getAllValues();
        assertFalse(savedSessions.isEmpty(), "at least one session must be saved");

        WhatsappSession saved = savedSessions.get(0);
        // CUSTOMER line has no storeId — must be null
        assertNull(saved.getStoreId(),
                "session saved for CUSTOMER line must have null storeId");
        assertEquals("customer_support", saved.getAgentName(),
                "session must carry customer_support agentName for CUSTOMER line");
    }

    // ---- T-17 test 5: Two-entry fixture — STORE and CUSTOMER entries in same event → distinct sessions ----

    @Test
    void twoEntries_storePlusCustomer_createDistinctSessionsWithCorrectLineContext() {
        when(whatsappLineService.findByPhoneNumberId(PHONE_ID_STORE))
                .thenReturn(Optional.of(storeLine()));
        when(whatsappLineService.findByPhoneNumberId(PHONE_ID_CUSTOMER))
                .thenReturn(Optional.of(customerLine()));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(aiCustomerService.isEnabled()).thenReturn(false); // AI disabled: focus on session routing

        // Session for STORE line — already exists so not a new session
        WhatsappSession storeSession = existingSession(FROM_STORE, PHONE_ID_STORE, false);
        storeSession.setStoreId(STORE_ID);
        when(whatsappSessionRepo.findByFromAndPhoneNumberId(FROM_STORE, PHONE_ID_STORE))
                .thenReturn(Optional.of(storeSession));

        // Session for CUSTOMER line — already exists
        WhatsappSession customerSession = existingSession(FROM_CUSTOMER, PHONE_ID_CUSTOMER, false);
        when(whatsappSessionRepo.findByFromAndPhoneNumberId(FROM_CUSTOMER, PHONE_ID_CUSTOMER))
                .thenReturn(Optional.of(customerSession));

        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileRepo.findByRole(any())).thenReturn(List.of());
        when(deviceRepo.findByUserIdIn(any())).thenReturn(List.of());

        // Fire the STORE payload
        WhatsappWebhookPayload storePayload = buildTextPayload(
                FROM_STORE, "Do you have burgers?", PHONE_ID_STORE, "Store Customer");
        handler.handleInbound(new WhatsappInboundEvent(this, storePayload));

        // Fire the CUSTOMER payload
        WhatsappWebhookPayload customerPayload = buildTextPayload(
                FROM_CUSTOMER, "Track my order", PHONE_ID_CUSTOMER, "Customer A");
        handler.handleInbound(new WhatsappInboundEvent(this, customerPayload));

        // Each line resolved via its own phoneNumberId
        verify(whatsappLineService).findByPhoneNumberId(PHONE_ID_STORE);
        verify(whatsappLineService).findByPhoneNumberId(PHONE_ID_CUSTOMER);

        // Sessions fetched by their compound keys
        verify(whatsappSessionRepo).findByFromAndPhoneNumberId(FROM_STORE, PHONE_ID_STORE);
        verify(whatsappSessionRepo).findByFromAndPhoneNumberId(FROM_CUSTOMER, PHONE_ID_CUSTOMER);

        // Verify session identities are separate (storeId on STORE session, null on CUSTOMER)
        assertEquals(STORE_ID, storeSession.getStoreId(),
                "STORE session must carry storeId");
        assertNull(customerSession.getStoreId(),
                "CUSTOMER session must have null storeId");
    }

    // ---- T-17 test 6: DRIVER line does NOT receive customer landing options (SA-5) ----

    @Test
    void driverLine_newSession_doesNotReceiveLandingOptions() {
        String phoneIdDriver = "wa-phone-driver-001";
        WhatsappLine driverLine = new WhatsappLine();
        driverLine.setPhoneNumberId(phoneIdDriver);
        driverLine.setAudience(Audience.DRIVER);
        driverLine.setAgentName("driver_support");
        driverLine.setActive(true);

        when(whatsappLineService.findByPhoneNumberId(phoneIdDriver))
                .thenReturn(Optional.of(driverLine));
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);

        // New session
        when(whatsappSessionRepo.findByFromAndPhoneNumberId(eq(FROM_STORE), eq(phoneIdDriver)))
                .thenReturn(Optional.empty());
        when(whatsappSessionRepo.findByFrom(FROM_STORE)).thenReturn(Optional.empty());
        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileRepo.findByRole(any())).thenReturn(List.of());
        when(deviceRepo.findByUserIdIn(any())).thenReturn(List.of());

        WhatsappWebhookPayload payload = buildTextPayload(
                FROM_STORE, "Hello driver", phoneIdDriver, "Driver A");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        // SA-5: sendLandingOptions must NOT be called for DRIVER lines.
        // Verify against the 6-arg overload — the handler always calls that one now,
        // so verifying the old 3-arg overload here would be vacuously true.
        verify(whatsappNotificationService, never())
                .sendLandingOptions(any(), any(), any(), any(), any(), any());
    }

    // ---- T-17 test 7: Unknown phoneNumberId falls back to CUSTOMER context, StoreAiAgent not called ----

    @Test
    void unknownPhoneId_fallsBackToCustomerContext_storeAgentNotInvoked() {
        String unknownPhone = "wa-unknown-line";
        when(whatsappLineService.findByPhoneNumberId(unknownPhone)).thenReturn(Optional.empty());
        when(verificationConsentService.isVerificationMessage(any())).thenReturn(false);
        when(aiCustomerService.isEnabled()).thenReturn(true);

        WhatsappSession session = existingSession(FROM_CUSTOMER, null, true);
        when(whatsappSessionRepo.findByFrom(FROM_CUSTOMER)).thenReturn(Optional.of(session));
        when(whatsappSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(aiCustomerService.handleWhatsappQueryForAgent(
                any(WhatsappWebhookPayload.Value.Message.class),
                eq(FROM_CUSTOMER), any(), eq("customer_support")))
                .thenReturn("Hi, how can I help?");

        WhatsappWebhookPayload payload = buildTextPayload(
                FROM_CUSTOMER, "Hello", unknownPhone, "Unknown Customer");

        handler.handleInbound(new WhatsappInboundEvent(this, payload));

        // StoreAiAgent must NEVER be called when lineContext falls back to CUSTOMER
        verify(storeAiAgent, never()).handleWhatsappQuery(any(), any(), any(), any());
    }
}
