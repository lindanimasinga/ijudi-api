package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Handles WhatsApp Cloud API webhooks (GET verification + POST inbound).
 * SEC-01: HMAC-SHA256 signature verification on every POST before parsing/publishing.
 */
@RestController
@RequestMapping("/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final String verifyToken;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final WhatsappConfig whatsappConfig;

    public WhatsAppWebhookController(@Value("${whatsapp.webhook.verify-token:}") String verifyToken,
                                     ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper,
                                     WhatsappConfig whatsappConfig) {
        this.verifyToken = verifyToken;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.whatsappConfig = whatsappConfig;
    }

    /** SEC-01: log startup ERROR when appSecret is missing. */
    @EventListener(ContextRefreshedEvent.class)
    public void checkAppSecretOnStartup() {
        if (whatsappConfig.appSecret() == null || whatsappConfig.appSecret().isBlank()) {
            LOG.error("SEC-01: whatsapp.cloud.appSecret is not configured — " +
                      "webhook signature verification will REJECT all inbound POSTs in production.");
        }
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
     * Receive incoming webhook POSTs from WhatsApp Cloud API.
     * SEC-01: HMAC-SHA256 signature is verified BEFORE the body is parsed or any event published.
     *
     * The raw body bytes are used for HMAC; the parsed object is used only after verification passes.
     */
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receiveWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody byte[] rawBody) {
        try {
            // SEC-01: verify signature before any parsing
            if (!verifyHmacSignature(rawBody, signatureHeader)) {
                LOG.warn("SEC-01: HMAC signature verification failed — rejecting webhook");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("invalid signature");
            }

            WhatsappWebhookPayload payload = objectMapper.readValue(rawBody, WhatsappWebhookPayload.class);
            LOG.info("Received WhatsApp webhook payload: {}", objectMapper.writeValueAsString(payload));
            var event = new WhatsappInboundEvent(this, payload);
            eventPublisher.publishEvent(event);
            return ResponseEntity.ok("received");
        } catch (Exception e) {
            LOG.error("Error handling WhatsApp webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }

    /**
     * SEC-01: verifies X-Hub-Signature-256 header.
     * Returns false (reject) when appSecret is missing.
     */
    boolean verifyHmacSignature(byte[] body, String signatureHeader) {
        String appSecret = whatsappConfig.appSecret();
        if (appSecret == null || appSecret.isBlank()) {
            LOG.error("SEC-01: appSecret not configured — rejecting inbound webhook");
            return false;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        // Header format: "sha256=<hex>"
        if (!signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String providedHex = signatureHeader.substring("sha256=".length());
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] expected = mac.doFinal(body);
            String expectedHex = HexFormat.of().formatHex(expected);
            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
                                         providedHex.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.error("SEC-01: HMAC computation failed", e);
            return false;
        }
    }
}
