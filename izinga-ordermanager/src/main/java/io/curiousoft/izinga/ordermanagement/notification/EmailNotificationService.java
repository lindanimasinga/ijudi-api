package io.curiousoft.izinga.ordermanagement.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.ordermanagement.service.StoreService;
import io.curiousoft.izinga.ordermanagement.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmailNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);
    private final UserProfileService userProfileService;
    private final StoreService storeService;
    private final String apiKey;
    private final String newOrderTemplateId;
    private final String notPaidTemplateId;
    private final RestTemplate restTemplate = new RestTemplate();

    public EmailNotificationService(@Value("${mailersend.apikey}") String apiKey,
                                    @Value("${mailersend.template.new-order}") String newOrderTemplateId,
                                    @Value("${mailersend.template.notpaid-order}") String notPaidTemplateId,
                                    UserProfileService userProfileService, StoreService storeService) {
        this.userProfileService = userProfileService;
        this.storeService = storeService;
        this.apiKey = apiKey;
        this.newOrderTemplateId = newOrderTemplateId;
        this.notPaidTemplateId = notPaidTemplateId;
    }

    @Async
    public void notifyAdminNewOrder(Order order) {
        try {
            notifyOrder(order, newOrderTemplateId);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public void notifyAdminOrderNotPaid(Order order) {
        try {
            notifyOrder(order, notPaidTemplateId);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Async
    public void notifyOrder(Order order, String templateId) throws JsonProcessingException {

        UserProfile customer = userProfileService.find(order.getCustomerId());
        StoreProfile store = storeService.find(order.getShopId());
        List<UserProfile> admins = userProfileService.findByRole(ProfileRoles.ADMIN);
        EmailRequest emailMessage = new EmailRequest();
        emailMessage.template_id = templateId;
        emailMessage.to = new ArrayList<>();
        emailMessage.personalization = new ArrayList<>();
        admins.forEach(admin -> {
            emailMessage.to.add(new To(admin.getEmailAddress()));
            Data data = new Data();
            data.order = order;
            data.account_name = "iZinga";
            data.customer = customer;
            data.items = order.getBasket().getItems();
            data.store = store;
            emailMessage.personalization.add(new Personalization(admin.getEmailAddress(), data));
        });

        try {
            LOGGER.info("Sending email using template " + templateId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", "Bearer " + apiKey);
            //Create a new HttpEntity
            final HttpEntity<EmailRequest> entity = new HttpEntity<>(emailMessage, headers);
            restTemplate.postForEntity("https://api.mailersend.com/v1/email",
                    entity, String.class);
        } catch (RestClientException e) {
            e.printStackTrace();
        }
    }
}
