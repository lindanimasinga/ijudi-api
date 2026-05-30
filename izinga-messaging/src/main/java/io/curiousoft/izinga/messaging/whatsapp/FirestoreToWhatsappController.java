package io.curiousoft.izinga.messaging.whatsapp;

import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.firebase.FirestoreService;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTextRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/forward")
public class FirestoreToWhatsappController {

    private static final Logger LOG = LoggerFactory.getLogger(FirestoreToWhatsappController.class);

    private final FirestoreService firestoreService;
    private final WhatsAppService whatsAppService;
    private final WhatsappConfig whatsappConfig;
    private final ConversationHistoryService conversationHistoryService;

    public FirestoreToWhatsappController(FirestoreService firestoreService, WhatsAppService whatsAppService,
                                         WhatsappConfig whatsappConfig,
                                         ConversationHistoryService conversationHistoryService) {
        this.firestoreService = firestoreService;
        this.whatsAppService = whatsAppService;
        this.whatsappConfig = whatsappConfig;
        this.conversationHistoryService = conversationHistoryService;
    }

    /**
     * Forward a message stored in Firestore to the customer via WhatsApp.
     * After a successful send, records the correction in conversation history and
     * appends it to the AI agent system prompt — both non-blocking.
     * Path: /forward/chatSession/{cSId}/message/{msgId}
     */
    @GetMapping(value = "/chatSession/{cSId}/message/{msgId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> forwardMessageToWhatsapp(@PathVariable("cSId") String cSId, @PathVariable("msgId") String msgId) {
        try {
            LOG.info("Forward request for chatSession={} messageId={}", cSId, msgId);

            ChatSession session = firestoreService.getChatSessionById(cSId);
            if (session == null) {
                LOG.warn("ChatSession not found: {}", cSId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "chatSession not found"));
            }

            FireStoreMessage msg = firestoreService.getMessageForSession(cSId, msgId);
            if (msg == null) {
                LOG.warn("Message not found: {}/{}", cSId, msgId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "message not found"));
            }

            if (msg.getMessageType() != null && msg.getMessageType() != FireStoreMessage.MessageType.TEXT) {
                LOG.warn("Forwarding non-text message types not implemented: {}", msg.getMessageType());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "unsupported message type"));
            }

            String normalizedTo = normalizePhone(session.getCustomerMobileNumber());

            // Send the human-written correction to the customer via WhatsApp
            WhatsappTextRequest req = new WhatsappTextRequest();
            req.setTo(normalizedTo);
            WhatsappTextRequest.Text t = new WhatsappTextRequest.Text();
            t.setBody(msg.getMessage());
            req.setText(t);

            LOG.info("Sending WhatsApp message to {} via phoneId={}", normalizedTo, whatsappConfig.phoneId());
            var resp = whatsAppService.sendTextMessage(whatsappConfig.phoneId(), req).execute();
            if (!resp.isSuccessful()) {
                LOG.error("Failed to forward message: {} {}", resp.code(),
                        resp.errorBody() != null ? resp.errorBody().string() : "");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "failed to send", "status", resp.code()));
            }

            // Post-send: record correction in conversation history (non-blocking)
            try {
                conversationHistoryService.recordHumanCorrection(normalizedTo, session.getCustomerName(), msg.getMessage());
            } catch (Exception ex) {
                LOG.warn("Could not update conversation history with correction: {}", ex.getMessage());
            }

            return ResponseEntity.ok(Map.of("status", "sent", "response", resp.body()));

        } catch (Exception e) {
            LOG.error("Error forwarding message to WhatsApp", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private static String normalizePhone(String phone) {
        return phone != null && phone.startsWith("0") ? phone.replaceFirst("0", "+27") : phone;
    }
}
