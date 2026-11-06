package io.curiousoft.izinga.messaging.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "whatsapp.cloud")
public record WhatsappConfig(String phoneId, String orderConfirmationCustomerTemplate,
                             String orderConfirmationShopTemplate,
                             String orderConfirmationMessengerTemplate,
                             /** REQ-03: phone_number_id for the driver-facing WhatsApp line */
                             String driverPhoneId,
                             /** REQ-03: enables multi-line routing; false = legacy byte-identical behavior */
                             Boolean multiLineEnabled,
                             /** SEC-01: Meta app secret for HMAC-SHA256 webhook signature verification */
                             String appSecret) {

    /**
     * AMD-01 (REQ-A3): returns {@code true} when appSecret is configured (non-null, non-blank),
     * meaning HMAC signature verification is genuinely active.
     *
     * ADMIN audience minting is gated on this predicate — when verification is inactive (production
     * bypass state), no extra DB read is performed and the configured audience is used unchanged.
     * The moment appSecret is configured and the app restarts, ADMIN minting activates with no
     * further code change.
     */
    public boolean isSignatureVerificationEnabled() {
        return appSecret != null && !appSecret.isBlank();
    }
}
