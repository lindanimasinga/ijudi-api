package io.curiousoft.izinga.ordermanagement.service.whatsapp;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.utils.IjudiUtils;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappTemplateRequest;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static java.lang.String.format;

@Service
public class WhatsappNotificationService implements AdminOnlyNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsappNotificationService.class);
    private final WhatsAppService whatsAppService;
    private final WhatsappConfig whatsappConfig;

    public WhatsappNotificationService(WhatsAppService whatsAppService,
                                       WhatsappConfig whatsappConfig) {
        this.whatsAppService = whatsAppService;
        this.whatsappConfig = whatsappConfig;
    }

    @Override
    public void sendMessage(String mobileNumber, String message) {

    }

    @Override
    public void notifyOrderPlaced(Order order, Profile userProfile) throws IOException {
        notifyOrderPlaced(whatsappConfig.orderConfirmationCustomerTemplate(), order, userProfile);
    }


    @Override
    public void notifyShopOrderPlaced(Order order, StoreProfile store) throws IOException {
        notifyOrderPlaced(whatsappConfig.orderConfirmationShopTemplate(), order, store);
    }

    private void notifyOrderPlaced(String templateName, Order order, Profile userProfile) throws IOException {
        // BODY parameters
        WhatsappTemplateRequest.Parameter nameParam = new WhatsappTemplateRequest.Parameter();
        nameParam.setText(userProfile.getName());
        nameParam.setType(WhatsappTemplateRequest.ParameterType.TEXT);

        WhatsappTemplateRequest.Parameter orderNumberParam = new WhatsappTemplateRequest.Parameter();
        orderNumberParam.setText("#" + order.getId());
        orderNumberParam.setType(WhatsappTemplateRequest.ParameterType.TEXT);

        WhatsappTemplateRequest.Component bodyComponent = new WhatsappTemplateRequest.Component();
        bodyComponent.setType(WhatsappTemplateRequest.ComponentType.BODY);
        bodyComponent.setParameters(List.of(nameParam, orderNumberParam));

        // BUTTON parameter
        WhatsappTemplateRequest.Parameter buttonParam = new WhatsappTemplateRequest.Parameter();
        buttonParam.setText(order.getId().toUpperCase()); // or "#K9RW9" etc.
        buttonParam.setType(WhatsappTemplateRequest.ParameterType.TEXT);

        WhatsappTemplateRequest.Component buttonComponent = new WhatsappTemplateRequest.Component();
        buttonComponent.setType(WhatsappTemplateRequest.ComponentType.BUTTON);
        buttonComponent.setSub_type(WhatsappTemplateRequest.ButtonSubType.URL); // or QUICK_REPLY depending on your template
        buttonComponent.setIndex(0);

        // Template
        WhatsappTemplateRequest.Template template = new WhatsappTemplateRequest.Template();
        template.setName(templateName);

        WhatsappTemplateRequest.Language language = new WhatsappTemplateRequest.Language();
        language.setCode("en_US"); // adjust based on your template
        template.setLanguage(language);

        template.setComponents(List.of(bodyComponent, buttonComponent));

        // Request
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(
                userProfile.getMobileNumber().startsWith("0")
                        ? userProfile.getMobileNumber().replaceFirst("0", "27")
                        : userProfile.getMobileNumber()
        );
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request)
                .execute();
    }
}
