package io.curiousoft.izinga.messaging.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final String verifyToken;
    private final ApplicationEventPublisher eventPublisher;

    public WhatsAppWebhookController(@Value("${whatsapp.webhook.verify-token:}") String verifyToken,
                                     ApplicationEventPublisher eventPublisher) {
        this.verifyToken = verifyToken;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Webhook verification endpoint used by Facebook/WhatsApp Cloud API.
     * Expects query params: hub.mode, hub.verify_token, hub.challenge
     */
    @GetMapping(value = "/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyWebhook(@RequestParam Map<String, String> params) {
        LOG.info("Webhook verification request received: {}", params);
        String mode = params.get("hub.mode");
        String token = params.get("hub.verify_token");
        String challenge = params.get("hub.challenge");

        if (mode != null && mode.equals("subscribe") && token != null && token.equals(verifyToken)) {
            LOG.info("Webhook verified successfully");
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }

        LOG.warn("Webhook verification failed: mode={}, providedTokenMatches={}", mode, token != null && token.equals(verifyToken));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    /**
     * Receive incoming webhook POSTs from WhatsApp Cloud API
     */
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receiveWebhook(@RequestBody WhatsappWebhookPayload payload) {
        try {
            LOG.info("Incoming WhatsApp webhook payload: {}", payload.getObject());
            // publish an application event so other modules can react
            var event = new WhatsappInboundEvent(this, payload);
            eventPublisher.publishEvent(event);
            return ResponseEntity.ok("received");
        } catch (Exception e) {
            LOG.error("Error handling WhatsApp webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}
