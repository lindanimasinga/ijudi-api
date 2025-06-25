package io.curiousoft.izinga.ordermanagement.service.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties(prefix = "whatsapp.cloud")
public record WhatsappConfig(String phoneId, String orderConfirmationCustomerTemplate,
                             String orderConfirmationShopTemplate,
                             String orderConfirmationMessengerTemplate) {
}
