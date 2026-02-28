package io.curiousoft.izinga.messaging.whatsapp;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.firebase.FireStoreTextMessage;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirestoreService;
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

@Service
public class WhatsappInboundEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappInboundEventHandler.class);

    private final ApplicationEventPublisher eventPublisher;
    private final UserProfileRepo userProfileRepo;
    private final FirestoreService firestoreService;
    private final FirebaseNotificationService firebaseNotificationService;
    private final DeviceRepository deviceRepo;

    public WhatsappInboundEventHandler(ApplicationEventPublisher eventPublisher, FirestoreService firestoreService,
                                       FirebaseNotificationService firebaseNotificationService, UserProfileRepo userProfileRepo,
                                       FirebaseNotificationService firebaseNotificationService1, DeviceRepository deviceRepo) {
        this.eventPublisher = eventPublisher;
        this.firestoreService = firestoreService;
        this.userProfileRepo = userProfileRepo;
        this.firebaseNotificationService = firebaseNotificationService1;
        this.deviceRepo = deviceRepo;
    }

    @Async
    @EventListener
    public void handleInbound(WhatsappInboundEvent event) {
        try {
            WhatsappWebhookPayload payload = event.getPayload();
            LOG.info("Handling inbound WhatsApp event: {}", payload.getObject());

            List<WhatsappWebhookPayload.Entry> entries = payload.getEntry();
            if (entries == null) return;

            for (WhatsappWebhookPayload.Entry entry : entries) {
                List<WhatsappWebhookPayload.Change> changes = entry.getChanges();
                if (changes == null) continue;
                for (WhatsappWebhookPayload.Change change : changes) {
                    var value = change.getValue();
                    if (value == null) continue;

                    List<WhatsappWebhookPayload.Value.Message> messages = value.getMessages();
                    if (messages != null) {
                        for (WhatsappWebhookPayload.Value.Message message : messages) {
                            String from = message.getFrom();
                            String id = message.getId();
                            String type = message.getType();
                            LOG.info("Received message from={} id={} type={}", from, id, type);

                            // delegate based on message type / content
                            if ("text".equals(type) || message.getText() != null) {
                                handleTextMessage(message, value.getContacts());
                                List<String> adminIds = userProfileRepo.findByRole(ProfileRoles.ADMIN)
                                        .stream().map(BaseModel::getId).toList();
                                List<Device> devices = deviceRepo.findByUserIdIn(adminIds);
                                PushMessage pushMessage = getPushMessage(from);
                                firebaseNotificationService.sendNotifications(devices, pushMessage);
                            } else if (message.getInteractive() != null) {
                                handleInteractiveMessage(message);
                            } else if ("location".equals(type) || message.getLocation() != null) {
                                handleLocationMessage(message);
                            } else if ("image".equals(type) || message.getImage() != null) {
                                handleImageMessage(message);
                            } else {
                                LOG.info("Unhandled message type={} from={}", type, from);
                            }
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

    private void handleTextMessage(WhatsappWebhookPayload.Value.Message message, List<WhatsappWebhookPayload.Value.Contact> contacts) {
        try {
            String from = message.getFrom();
            if (message.getText() != null) {
                String body = message.getText().getBody();
                LOG.info("Text message from {}: {}", from, body);

                // prepare Firestore message
                FireStoreTextMessage msg = FireStoreTextMessage.builder()
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
                if (!meta.isEmpty()) msg.setMeta(meta);

                // Use the phone number as the customer identifier (per requirements)
                try {
                    String createdMsgId;
                    if (contactName != null) {
                        createdMsgId = firestoreService.writeMessageForCustomer(from, contactName, msg);
                    } else {
                        createdMsgId = firestoreService.writeMessageForCustomer(from, "Customer " + from, msg);
                    }
                    LOG.info("Written text message to firestore for customer {} messageId={}", from, createdMsgId);
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

    private void handleImageMessage(WhatsappWebhookPayload.Value.Message message) {
        try {
            String from = message.getFrom();
            var img = message.getImage();
            if (img != null) {
                LOG.info("Image message from {}: id={} url={} caption={}", from, img.getId(), img.getUrl(), img.getCaption());

                FireStoreTextMessage imgMsg = FireStoreTextMessage.builder()
                        .createdAt(Instant.now())
                        .isRead(false)
                        .message(img.getCaption())
                        .messageType(FireStoreTextMessage.MessageType.IMAGE)
                        .senderId(from)
                        .senderType(FireStoreTextMessage.SenderType.CUSTOMER)
                        .timestamp(Instant.now())
                        .build();
                Map<String, Object> meta = new HashMap<>();
                if (img.getUrl() != null) meta.put("url", img.getUrl());
                if (img.getId() != null) meta.put("mediaId", img.getId());
                if (!meta.isEmpty()) imgMsg.setMeta(meta);
                try {
                   // firestoreService.writeMessageForCustomer(from, imgMsg);
                } catch (Exception e) {
                    LOG.warn("Failed to persist image message for {}", from, e);
                }
            }
        } catch (Exception e) {
            LOG.error("Error handling image message", e);
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
