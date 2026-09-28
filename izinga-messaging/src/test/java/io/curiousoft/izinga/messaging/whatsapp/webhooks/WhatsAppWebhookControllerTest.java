package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock ApplicationEventPublisher eventPublisher;

    private static final String APP_SECRET = "test-app-secret";
    private WhatsappConfig config;
    private WhatsAppWebhookController controller;

    @BeforeEach
    void setUp() {
        config = new WhatsappConfig("phone-id", null, null, null, null, false, APP_SECRET);
        controller = new WhatsAppWebhookController("verify-token", eventPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper(), config);
    }

    @Test
    void validSignature_returns200() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String sig = "sha256=" + hmacHex(APP_SECRET, body);
        var response = controller.receiveWebhook(sig, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void missingSignature_returns403() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        var response = controller.receiveWebhook(null, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void invalidSignature_returns403() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        var response = controller.receiveWebhook("sha256=deadbeef", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(eventPublisher);
    }

    /**
     * SEC-01 bypass: when appSecret is null the request MUST be accepted (not rejected).
     * The bypass is intentionally self-healing — full verification resumes once appSecret
     * is configured, with no further code change needed.
     */
    @Test
    void appSecretNull_acceptsRequestWithWarning() {
        WhatsappConfig noSecretConfig = new WhatsappConfig("phone-id", null, null, null, null, false, null);
        var ctrl = new WhatsAppWebhookController("token", eventPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper(), noSecretConfig);
        byte[] body = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        var response = ctrl.receiveWebhook("sha256=anything", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(eventPublisher).publishEvent(any());
    }

    /** SEC-01 bypass: blank appSecret (empty string) also triggers the bypass — same as null. */
    @Test
    void appSecretBlank_acceptsRequestWithWarning() {
        WhatsappConfig blankSecretConfig = new WhatsappConfig("phone-id", null, null, null, null, false, "");
        var ctrl = new WhatsAppWebhookController("token", eventPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper(), blankSecretConfig);
        byte[] body = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        var response = ctrl.receiveWebhook("sha256=anything", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(eventPublisher).publishEvent(any());
    }

    /** verifyHmacSignature low-level: null appSecret returns true (bypass). */
    @Test
    void verifyHmacSignature_returnsTrue_whenAppSecretNull() {
        WhatsappConfig nullSecretConfig = new WhatsappConfig("phone-id", null, null, null, null, false, null);
        var ctrl = new WhatsAppWebhookController("token", eventPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper(), nullSecretConfig);
        assertThat(ctrl.verifyHmacSignature("{}".getBytes(StandardCharsets.UTF_8), "sha256=anything")).isTrue();
    }

    /** verifyHmacSignature low-level: blank appSecret returns true (bypass). */
    @Test
    void verifyHmacSignature_returnsTrue_whenAppSecretBlank() {
        WhatsappConfig blankSecretConfig = new WhatsappConfig("phone-id", null, null, null, null, false, "");
        var ctrl = new WhatsAppWebhookController("token", eventPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper(), blankSecretConfig);
        assertThat(ctrl.verifyHmacSignature("{}".getBytes(StandardCharsets.UTF_8), "sha256=anything")).isTrue();
    }

    @Test
    void verifyHmacSignature_returnsTrue_whenCorrect() throws Exception {
        byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
        String sig = "sha256=" + hmacHex(APP_SECRET, body);
        assertThat(controller.verifyHmacSignature(body, sig)).isTrue();
    }

    @Test
    void verifyHmacSignature_returnsFalse_whenWrongPrefix() {
        assertThat(controller.verifyHmacSignature("{}".getBytes(), "md5=abc")).isFalse();
    }

    private static String hmacHex(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }
}
