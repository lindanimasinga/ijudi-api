package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.aiAgent.AiCustomerServiceAgent;
import io.curiousoft.izinga.messaging.aiAgent.StoreAiAgent;
import io.curiousoft.izinga.messaging.firebase.FireStoreTextMessage;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirestoreService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.messaging.whatsapp.lines.LineContext;
import io.curiousoft.izinga.messaging.whatsapp.lines.WhatsappLine;
import io.curiousoft.izinga.messaging.whatsapp.lines.WhatsappLineService;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateReplyEvent;
import io.curiousoft.izinga.messaging.whatsapp.verification.VerificationConsentService;
import io.curiousoft.izinga.messaging.repo.WhatsappSessionRepo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WhatsappInboundEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappInboundEventHandler.class);

    private final ApplicationEventPublisher eventPublisher;
    private final UserProfileRepo userProfileRepo;
    private final FirestoreService firestoreService;
    private final FirebaseNotificationService firebaseNotificationService;
    private final DeviceRepository deviceRepo;
    private final WhatsappNotificationService whatsappNotificationService;
    private final WhatsappSessionRepo whatsappSessionRepo;
    private final VerificationConsentService verificationConsentService;
    private final AiCustomerServiceAgent aiCustomerService;
    /** T-11: store-scoped AI agent for STORE audience lines. */
    private final StoreAiAgent storeAiAgent;
    private final WhatsappImageDocumentService whatsappImageDocumentService;
    /** REQ-10: line resolution service */
    private final WhatsappLineService whatsappLineService;

    public WhatsappInboundEventHandler(ApplicationEventPublisher eventPublisher, FirestoreService firestoreService,
                                       FirebaseNotificationService firebaseNotificationService, UserProfileRepo userProfileRepo,
                                       DeviceRepository deviceRepo,
                                       WhatsappNotificationService whatsappNotificationService, WhatsappSessionRepo whatsappSessionRepo,
                                       VerificationConsentService verificationConsentService, AiCustomerServiceAgent aiCustomerService,
                                       StoreAiAgent storeAiAgent,
                                       WhatsappImageDocumentService whatsappImageDocumentService,
                                       WhatsappLineService whatsappLineService) {
        this.eventPublisher = eventPublisher;
        this.firestoreService = firestoreService;
        this.userProfileRepo = userProfileRepo;
        this.firebaseNotificationService = firebaseNotificationService;
        this.deviceRepo = deviceRepo;
        this.whatsappNotificationService = whatsappNotificationService;
        this.whatsappSessionRepo = whatsappSessionRepo;
        this.verificationConsentService = verificationConsentService;
        this.aiCustomerService = aiCustomerService;
        this.storeAiAgent = storeAiAgent;
        this.whatsappImageDocumentService = whatsappImageDocumentService;
        this.whatsappLineService = whatsappLineService;
    }

    @Async
    @EventListener
    public void handleInbound(WhatsappInboundEvent event) {
        try {
            WhatsappWebhookPayload payload = event.getPayload();
            LOG.info("Handling inbound WhatsApp event: {}", payload.getObject());

            // REQ-22: handle account_update webhook type
            if ("account_update".equals(payload.getObject())) {
                handleAccountUpdate(payload);
                return;
            }

            List<WhatsappWebhookPayload.Entry> entries = payload.getEntry();
            if (entries == null) return;

            for (WhatsappWebhookPayload.Entry entry : entries) {
                List<WhatsappWebhookPayload.Change> changes = entry.getChanges();
                if (changes == null) continue;
                for (WhatsappWebhookPayload.Change change : changes) {
                    var value = change.getValue();
                    if (value == null) continue;

                    // REQ-10: read metadata.phone_number_id and resolve LineContext
                    String phoneNumberId = value.getMetadata() != null ? value.getMetadata().getPhoneNumberId() : null;
                    LineContext lineContext = resolveLineContext(phoneNumberId);

                    // REQ-23: DEBUG log per inbound
                    LOG.debug("REQ-23 inbound: phoneNumberId={} agentName={} audience={}",
                            phoneNumberId, lineContext.agentName(), lineContext.audience());

                    List<WhatsappWebhookPayload.Value.Message> messages = value.getMessages();
                    if (messages != null) {
                        for (WhatsappWebhookPayload.Value.Message message : messages) {
                            processInboundMessage(message, value.getContacts(), lineContext);
                        }
                    }

                    List<WhatsappWebhookPayload.Value.Status> statuses = value.getStatuses();
                    handleStatuses(statuses);
                }
            }
        } catch (Exception e) {
            LOG.error("Error handling inbound WhatsApp event", e);
        }
    }

    /**
     * REQ-10: resolve LineContext from phoneNumberId.
     * Falls back to a synthetic CUSTOMER/customer_support context when unknown.
     */
    private LineContext resolveLineContext(String phoneNumberId) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            return new LineContext(null, "customer_support", Audience.CUSTOMER, null);
        }
        Optional<WhatsappLine> lineOpt = whatsappLineService.findByPhoneNumberId(phoneNumberId);
        if (lineOpt.isPresent()) {
            WhatsappLine line = lineOpt.get();
            return new LineContext(line, line.getAgentName(), line.getAudience(), line.getStoreId());
        }
        // unknown phoneNumberId — fallback logged in WhatsappLineService
        return new LineContext(null, "customer_support", Audience.CUSTOMER, null);
    }

    /** REQ-22 / T-15: structured INFO log for account_update webhook objects. Includes storeId for STORE lines. */
    private void handleAccountUpdate(WhatsappWebhookPayload payload) {
        try {
            if (payload.getEntry() == null) return;
            for (var entry : payload.getEntry()) {
                if (entry.getChanges() == null) continue;
                for (var change : entry.getChanges()) {
                    if (change.getValue() == null) continue;
                    var meta = change.getValue().getMetadata();
                    String phoneNumberId = meta != null ? meta.getPhoneNumberId() : "unknown";
                    // T-15: resolve LineContext to include storeId for STORE lines
                    LineContext lineContext = resolveLineContext(phoneNumberId);
                    String storeId = lineContext.storeId();
                    LOG.info("REQ-22 account_update: phoneNumberId={} field={} audience={} storeId={} timestamp={}",
                            phoneNumberId, change.getField(), lineContext.audience(), storeId, Instant.now());
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to process account_update payload", e);
        }
    }

    /** REQ-10: lineContext threaded through all downstream calls. SA-3: explicit param, never @RequestScope. */
    private void processInboundMessage(WhatsappWebhookPayload.Value.Message message,
                                       List<WhatsappWebhookPayload.Value.Contact> contacts,
                                       LineContext lineContext) {
        String from = message.getFrom();
        String id = message.getId();
        String type = message.getType();
        LOG.info("Received message from={} id={} type={}", from, id, type);

        var session = upsertSession(from, lineContext);
        String aiResponseToCustomer = handlePreDispatchFlows(message, contacts, session, lineContext);
        dispatchMessageByType(message, contacts, aiResponseToCustomer, lineContext);
    }

    /** REQ-10 / SA-5: lineContext threaded; landing options audience-aware. */
    private String handlePreDispatchFlows(WhatsappWebhookPayload.Value.Message message,
                                          List<WhatsappWebhookPayload.Value.Contact> contacts,
                                          WhatsappSession session,
                                          LineContext lineContext) {
        String from = message.getFrom();
        LOG.info("Checking if message from {} is a verification consent reply", from);
        var isVerificationMessage = verificationConsentService.isVerificationMessage(message);
        boolean isAiCustomerServiceEnabled = aiCustomerService.isEnabled();
        String aiResponseToCustomer = null;

        if (isVerificationMessage) {
            LOG.info("Received verification consent reply from {}", from);
            verificationConsentService.handleVerificationConsentReply(message, from);
            whatsappNotificationService.sendMessage(from, "Thank you. Your application is being processed.",
                    lineContext.audience(), lineContext.storeId());
            return null;
        }

        if (session.isNewSession()) {
            LOG.info("New WhatsApp session started for {}", from);
            // SA-5: DRIVER line must not receive the customer landing menu
            if (lineContext.audience() != Audience.DRIVER) {
                var user = userProfileRepo.findByMobileNumber(from);
                whatsappNotificationService.sendLandingOptions(from, extractContactName(contacts), user,
                        lineContext.audience(), lineContext.storeId());
            } else {
                LOG.info("SA-5: DRIVER line — skipping customer landing options for {}", from);
            }
            return null;
        }

        if (isAiCustomerServiceEnabled && session.isAIAgentActive()) {
            LOG.info("AI customer service enabled, processing message from {} via agent={} audience={}",
                    from, lineContext.agentName(), lineContext.audience());
            // T-11: route STORE audience to StoreAiAgent; all others to AiCustomerServiceAgent
            if (lineContext.audience() == Audience.STORE) {
                aiResponseToCustomer = storeAiAgent.handleWhatsappQuery(
                        message, from, lineContext.storeId(), lineContext.agentName());
            } else {
                // REQ-20: DRIVER / CUSTOMER audiences use agent-aware handler
                aiResponseToCustomer = aiCustomerService.handleWhatsappQueryForAgent(
                        message, from, null, lineContext.agentName());
            }
            if (aiResponseToCustomer != null) {
                whatsappNotificationService.sendMessage(from, aiResponseToCustomer,
                        lineContext.audience(), lineContext.storeId());
            }
        }
        return aiResponseToCustomer;
    }

    private void dispatchMessageByType(WhatsappWebhookPayload.Value.Message message,
                                       List<WhatsappWebhookPayload.Value.Contact> contacts,
                                       String aiResponseToCustomer,
                                       LineContext lineContext) {
        String type = message.getType();
        String from = message.getFrom();

        if ("text".equals(type) || message.getText() != null) {
            handleTextMessage(message, contacts, aiResponseToCustomer);
            notifyAdminsForInboundText(from);
            return;
        }
        if (message.getInteractive() != null) {
            handleInteractiveMessage(message);
            return;
        }
        if ("location".equals(type) || message.getLocation() != null) {
            handleLocationMessage(message);
            return;
        }
        if ("image".equals(type) || message.getImage() != null) {
            handleImageMessage(message, contacts, lineContext);
            return;
        }

        LOG.info("Unhandled message type={} from={}", type, from);
    }

    private void notifyAdminsForInboundText(String from) {
        List<String> adminIds = userProfileRepo.findByRole(ProfileRoles.ADMIN)
                .stream().map(BaseModel::getId).toList();
        List<Device> devices = deviceRepo.findByUserIdIn(adminIds);
        PushMessage pushMessage = getPushMessage(from);
        firebaseNotificationService.sendNotifications(devices, pushMessage);
    }

    /** SEC-02 / REQ-14: lookup by (from, phoneNumberId); stamp missing phoneNumberId on legacy docs. */
    private WhatsappSession upsertSession(String from, LineContext lineContext) {
        String phoneNumberId = lineContext.line() != null ? lineContext.line().getPhoneNumberId() : null;
        var now = Instant.now();
        WhatsappSession session;

        if (phoneNumberId != null) {
            // Try compound key first (SEC-02)
            var opt = whatsappSessionRepo.findByFromAndPhoneNumberId(from, phoneNumberId);
            if (opt.isPresent()) {
                session = opt.get();
            } else {
                // REQ-14: legacy adoption — look up by from only, stamp if found without phoneNumberId
                var legacyOpt = whatsappSessionRepo.findByFrom(from);
                if (legacyOpt.isPresent() && legacyOpt.get().getPhoneNumberId() == null) {
                    session = legacyOpt.get();
                    session.setPhoneNumberId(phoneNumberId);
                    session.setAgentName(lineContext.agentName());
                    session.setStoreId(lineContext.storeId());
                    LOG.debug("REQ-14: stamped legacy session for {} with phoneNumberId={}", from, phoneNumberId);
                } else {
                    session = new WhatsappSession(from);
                    session.setPhoneNumberId(phoneNumberId);
                    session.setAgentName(lineContext.agentName());
                    session.setStoreId(lineContext.storeId());
                }
            }
        } else {
            // No phoneNumberId: legacy path
            var opt = whatsappSessionRepo.findByFrom(from);
            session = opt.orElseGet(() -> new WhatsappSession(from));
        }

        session.setNewSession(session.getLastMessageDate() == null || now.minusSeconds(90 * 60).isAfter(session.getLastMessageDate()));
        session.setLastMessageDate(now);
        whatsappSessionRepo.save(session);
        return session;
    }

    @NotNull
    private static PushMessage getPushMessage(String from) {
        PushHeading pushHeading = new PushHeading(
                "New message from WhatsApp customer " + from,
                "New WhatsApp Message",
                null,
                null);

        PushMessage pushMessage = new PushMessage(
                PushMessageType.WHATSAPP,
                pushHeading,
                String.format("https://onboard.izinga.co.za/messaging/whatsapp/%s", from)
        );
        return pushMessage;
    }

    private void handleTextMessage(WhatsappWebhookPayload.Value.Message message,
                                   List<WhatsappWebhookPayload.Value.Contact> contacts, String aiResponseToCustomer) {
        try {
            String from = message.getFrom();
            if (message.getText() != null) {
                String body = message.getText().getBody();
                LOG.info("Text message from {}: {}", from, body);

                // prepare Firestore message
                FireStoreTextMessage customerMessage = FireStoreTextMessage.builder()
                        .createdAt(Instant.now())
                        .isRead(false)
                        .message(body)
                        .messageType(FireStoreTextMessage.MessageType.TEXT)
                        .senderId(from)
                        .senderType(FireStoreTextMessage.SenderType.CUSTOMER)
                        .timestamp(Instant.now())
                        .build();

                // include contact name (if provided) in meta
                Map<String, Object> meta = new HashMap<>();
                String contactName = null;
                if (contacts != null && !contacts.isEmpty()) {
                    try {
                        var c = contacts.get(0);
                        if (c != null && c.getProfile() != null && c.getProfile().getName() != null) {
                            contactName = c.getProfile().getName();
                            meta.put("contactName", contactName);
                        }
                        if (c != null && c.getWaId() != null) {
                            meta.put("waId", c.getWaId());
                        }
                    } catch (Exception ex) {
                        LOG.debug("Failed to extract contact info", ex);
                    }
                }
                if (!meta.isEmpty()) customerMessage.setMeta(meta);

                // Use the phone number as the customer identifier (per requirements)
                try {
                    String createdMsgId = contactName != null ? firestoreService.writeMessageForCustomer(from, contactName, customerMessage)
                            : firestoreService.writeMessageForCustomer(from, "Customer " + from, customerMessage);
                    LOG.info("Written text message to firestore for customer {} messageId={}", from, createdMsgId);

                    if(aiResponseToCustomer != null && !aiResponseToCustomer.isBlank()) {
                        FireStoreTextMessage aiMessage = FireStoreTextMessage.builder()
                                .createdAt(Instant.now())
                                .isRead(false)
                                .message(aiResponseToCustomer)
                                .messageType(FireStoreTextMessage.MessageType.TEXT)
                                .senderId("AI_AGENT")
                                .senderType(FireStoreTextMessage.SenderType.STORE)
                                .timestamp(Instant.now())
                                .build();
                        String aiMsgId = firestoreService.writeMessageForCustomer(from, "AI Agent", aiMessage);
                        LOG.info("Written AI response message to firestore for customer {} messageId={}", from, aiMsgId);

                    }
                } catch (Exception e) {
                    LOG.error("Failed to write text message to firestore for from={}", from, e);
                }
            }
        } catch (Exception e) {
            LOG.error("Error handling text message", e);
        }
    }

    private void handleInteractiveMessage(WhatsappWebhookPayload.Value.Message message) {
        try {
            String from = message.getFrom();
            var interactive = message.getInteractive();
            if (interactive == null) return;

            String interactiveType = interactive.getType();
            var buttonReply = interactive.getButtonReply();
            var listReply = interactive.getListReply();

            if ("button_reply".equals(interactiveType) && buttonReply != null) {
                String buttonId = buttonReply.getId();
                String buttonTitle = buttonReply.getTitle();
                LOG.info("Button reply from {} id={} title={}", from, buttonId, buttonTitle);

                if (containsAccept(buttonId) || containsAccept(buttonTitle)) {
                    LOG.info("Detected quote acceptance from {} (button)", from);
                    var replyEvent = new WhatsappTemplateReplyEvent(this, from, buttonId, buttonTitle, message);
                    eventPublisher.publishEvent(replyEvent);
                }
            } else if ("list_reply".equals(interactiveType) && listReply != null) {
                String itemId = listReply.getId();
                String itemTitle = listReply.getTitle();
                LOG.info("List reply from {} id={} title={}", from, itemId, itemTitle);
                if (containsAccept(itemId) || containsAccept(itemTitle)) {
                    LOG.info("Detected quote acceptance from {} (list)", from);
                    var replyEvent = new WhatsappTemplateReplyEvent(this, from, itemId, itemTitle, message);
                    eventPublisher.publishEvent(replyEvent);
                }
            } else {
                LOG.info("Unhandled interactive type {} from {}", interactiveType, from);
            }
        } catch (Exception e) {
            LOG.error("Error handling interactive message", e);
        }
    }

    private void handleLocationMessage(WhatsappWebhookPayload.Value.Message message) {
        try {
            String from = message.getFrom();
            var loc = message.getLocation();
            if (loc != null) {
                LOG.info("Location message from {}: name={} address={} lat={} lon={}",
                        from, loc.getName(), loc.getAddress(), loc.getLatitude(), loc.getLongitude());

                // Optionally write location as a message to firestore
                FireStoreTextMessage locMsg = FireStoreTextMessage.builder()
                        .createdAt(Instant.now())
                        .isRead(false)
                        .message(String.format("[location] %s (%s,%s)", loc.getName(), loc.getLatitude(), loc.getLongitude()))
                        .messageType(FireStoreTextMessage.MessageType.LOCATION)
                        .senderId(from)
                        .senderType(FireStoreTextMessage.SenderType.CUSTOMER)
                        .timestamp(Instant.now())
                        .build();
                try {
                    //firestoreService.writeMessageForCustomer(from, locMsg);
                } catch (Exception e) {
                    LOG.warn("Failed to persist location message for {}", from, e);
                }
            }
        } catch (Exception e) {
            LOG.error("Error handling location message", e);
        }
    }

    private String extractContactName(List<WhatsappWebhookPayload.Value.Contact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return null;
        }
        var contact = contacts.get(0);
        if (contact == null || contact.getProfile() == null) {
            return null;
        }
        return contact.getProfile().getName();
    }

    /** REQ-20: lineContext threaded to select the correct agent when handling image uploads. */
    private void handleImageMessage(WhatsappWebhookPayload.Value.Message message,
                                    List<WhatsappWebhookPayload.Value.Contact> contacts,
                                    LineContext lineContext) {
        String from = message.getFrom();
        try {
            var img = message.getImage();
            if (img != null) {
                LOG.info("Image message from {}: id={} url={} caption={}", from, img.getId(), img.getUrl(), img.getCaption());

                WhatsappImageProcessResult processResult = whatsappImageDocumentService.processImageAndTagUser(from, img);

                FireStoreTextMessage imgMsg = FireStoreTextMessage.builder()
                        .createdAt(Instant.now())
                        .isRead(false)
                        .message(img.getCaption() != null ? img.getCaption() : "Image uploaded")
                        .messageType(FireStoreTextMessage.MessageType.IMAGE)
                        .senderId(from)
                        .senderType(FireStoreTextMessage.SenderType.CUSTOMER)
                        .timestamp(Instant.now())
                        .build();
                Map<String, Object> meta = new HashMap<>();
                meta.put("uploadedUrl", processResult.uploadedUrl());
                meta.put("tagField", processResult.tagField());
                if (processResult.mediaId() != null) meta.put("mediaId", processResult.mediaId());
                if (processResult.mimeType() != null) meta.put("mimeType", processResult.mimeType());
                if (!meta.isEmpty()) imgMsg.setMeta(meta);
                try {
                    String contactName = extractContactName(contacts);
                    firestoreService.writeMessageForCustomer(
                            from,
                            contactName != null ? contactName : "Customer " + from,
                            imgMsg
                    );
                } catch (Exception e) {
                    LOG.warn("Failed to persist image message for {}", from, e);
                }

                String customerName = extractContactName(contacts);
                String aiReply = null;
                if (aiCustomerService.isEnabled()) {
                    String aiEventMessage = String.format(
                            "Customer uploaded document successfully. Field: %s, MediaId: %s, FileType: %s. " +
                                    "Please confirm upload success and guide the user on next required step.",
                            processResult.tagField(),
                            processResult.mediaId(),
                            processResult.mimeType()
                    );
                    // REQ-20: use agent-aware handler so image responses use the correct agent
                    aiReply = aiCustomerService.handleWhatsappQueryForAgent(aiEventMessage, from, customerName, lineContext.agentName());
                }

                whatsappNotificationService.sendMessage(
                        from,
                        aiReply != null && !aiReply.isBlank()
                                ? aiReply
                                : "Thanks. Your document image was received and linked to your profile.",
                        lineContext.audience(), lineContext.storeId()
                );
            }
        } catch (Exception e) {
            LOG.error("Error handling image message", e);
            sendImageProcessingFallback(from, lineContext);
        }
    }

    private void sendImageProcessingFallback(String from, LineContext lineContext) {
        try {
            whatsappNotificationService.sendMessage(
                    from,
                    "We could not process your image just now. Please send it again, or reply HELP for assistance.",
                    lineContext.audience(), lineContext.storeId()
            );
        } catch (Exception ex) {
            LOG.warn("Failed to send image-processing fallback message to {}", from, ex);
        }
    }

    private void handleStatuses(List<WhatsappWebhookPayload.Value.Status> statuses) {
        try {
            if (statuses == null) return;
            for (WhatsappWebhookPayload.Value.Status status : statuses) {
                LOG.info("Message status update: {}", status);
            }
        } catch (Exception e) {
            LOG.error("Error handling statuses", e);
        }
    }

    private static boolean containsAccept(String s) {
        return s != null && s.toLowerCase().contains("accept");
    }
}
