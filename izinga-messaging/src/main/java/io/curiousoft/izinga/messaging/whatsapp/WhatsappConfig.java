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
}
