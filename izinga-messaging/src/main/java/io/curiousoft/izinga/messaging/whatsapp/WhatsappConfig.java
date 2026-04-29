package io.curiousoft.izinga.messaging.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "whatsapp.cloud")
public record WhatsappConfig(String phoneId, String orderConfirmationCustomerTemplate,
                             String orderConfirmationShopTemplate,
                             String orderConfirmationMessengerTemplate) {
}
