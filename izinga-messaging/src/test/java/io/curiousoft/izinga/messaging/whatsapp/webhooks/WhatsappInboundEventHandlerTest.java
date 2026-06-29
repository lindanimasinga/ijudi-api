package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import io.curiousoft.izinga.commons.model.WhatsappSession;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.aiAgent.AiCustomerServiceAgent;
import io.curiousoft.izinga.messaging.firebase.FireStoreTextMessage;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirestoreService;
import io.curiousoft.izinga.messaging.repo.WhatsappSessionRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
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
import static org.mockito.Mockito.never;
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
    private WhatsappImageDocumentService whatsappImageDocumentService;

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
                whatsappImageDocumentService
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
        when(aiCustomerService.handleWhatsappQuery(anyString(), eq(FROM), eq("Lindani")))
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

        verify(aiCustomerService).handleWhatsappQuery(
                contains("Field: driverLicenseDocument"),
                eq(FROM),
                eq("Lindani")
        );
        verify(whatsappNotificationService).sendMessage(
                FROM,
                "Thanks. Your driver's license document is linked."
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
                "We could not process your image just now. Please send it again, or reply HELP for assistance."
        );
        verify(firestoreService, never()).writeMessageForCustomer(anyString(), anyString(), any());
        verify(aiCustomerService, never()).handleWhatsappQuery(anyString(), anyString(), anyString());
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
}
