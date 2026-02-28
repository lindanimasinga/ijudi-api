package io.curiousoft.izinga.messaging.whatsapp;

import io.curiousoft.izinga.messaging.firebase.FirestoreService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppService;
import io.curiousoft.izinga.messaging.whatsapp.Message;
import io.curiousoft.izinga.messaging.whatsapp.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/forward")
public class FirestoreToWhatsappController {

    private static final Logger LOG = LoggerFactory.getLogger(FirestoreToWhatsappController.class);

    private final FirestoreService firestoreService;
    private final WhatsAppService whatsAppService;
    private final WhatsappConfig whatsappConfig;

    public FirestoreToWhatsappController(FirestoreService firestoreService, WhatsAppService whatsAppService, WhatsappConfig whatsappConfig) {
        this.firestoreService = firestoreService;
        this.whatsAppService = whatsAppService;
        this.whatsappConfig = whatsappConfig;
    }

    /**
     * Forward a message stored in Firestore to the customer via WhatsApp.
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

            Message msg = firestoreService.getMessageForSession(cSId, msgId);
            if (msg == null) {
                LOG.warn("Message not found: {}/{}", cSId, msgId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "message not found"));
            }

            if (msg.getMessageType() == Message.MessageType.TEXT || msg.getMessageType() == null) {
                // Build the WhatsApp text message payload per Cloud API
                String to = session.getCustomerMobileNumber();
                String normalizedTo = to != null && to.startsWith("0") ? to.replaceFirst("0", "+27") : to;

                WhatsappTextRequest req = new WhatsappTextRequest();
                req.setTo(normalizedTo);
                WhatsappTextRequest.Text t = new WhatsappTextRequest.Text();
                t.setBody(msg.getMessage());
                req.setText(t);

                // send via WhatsApp API using configured phoneId
                String phoneId = whatsappConfig.phoneId();
                LOG.info("Sending WhatsApp message to {} via phoneId={}", normalizedTo, phoneId);
                var call = whatsAppService.sendTextMessage(phoneId, req);
                var resp = call.execute();
                if (resp.isSuccessful()) {
                    LOG.info("Message forwarded successfully: {}", resp.body());
                    return ResponseEntity.ok(Map.of("status", "sent", "response", resp.body()));
                } else {
                    LOG.error("Failed to forward message: {} {}", resp.code(), resp.errorBody() != null ? resp.errorBody().string() : "");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "failed to send", "status", resp.code()));
                }
            } else {
                LOG.warn("Forwarding non-text message types not implemented: {}", msg.getMessageType());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "unsupported message type"));
            }

        } catch (Exception e) {
            LOG.error("Error forwarding message to WhatsApp", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
