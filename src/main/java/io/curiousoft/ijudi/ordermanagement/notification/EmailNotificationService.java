package io.curiousoft.ijudi.ordermanagement.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailersend.sdk.MailerSend;
import com.mailersend.sdk.Recipient;
import com.mailersend.sdk.emails.Email;
import com.mailersend.sdk.exceptions.MailerSendException;
import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.ProfileRoles;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;
import io.curiousoft.ijudi.ordermanagement.service.StoreService;
import io.curiousoft.ijudi.ordermanagement.service.UserProfileService;
import io.curiousoft.ijudi.ordermanagement.service.zoomsms.ZoomResponse;
import io.curiousoft.ijudi.ordermanagement.service.zoomsms.ZoomSMSMessage;
import io.curiousoft.ijudi.ordermanagement.service.zoomsms.ZoomSmsNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmailNotificationService {

    private static Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);
    private UserProfileService userProfileService;
    private StoreService storeService;
    private String apiKey;
    private String newOrderTemplateId;
    private String notPaidTemplateId;
    private RestTemplate restTemplate = new RestTemplate();

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
