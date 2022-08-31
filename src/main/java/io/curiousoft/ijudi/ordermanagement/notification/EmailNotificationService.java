package io.curiousoft.ijudi.ordermanagement.notification;

import com.mailersend.sdk.MailerSend;
import com.mailersend.sdk.emails.Email;
import com.mailersend.sdk.exceptions.MailerSendException;
import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.ProfileRoles;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;
import io.curiousoft.ijudi.ordermanagement.service.StoreService;
import io.curiousoft.ijudi.ordermanagement.service.UserProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailNotificationService {

    private UserProfileService userProfileService;
    private StoreService storeService;
    private String apiKey;
    private String newOrderTemplateId;
    private String notPaidTemplateId;

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
        notifyOrder(order, newOrderTemplateId);
    }

    public void notifyAdminOrderNotPaid(Order order) {
        notifyOrder(order, notPaidTemplateId);
    }

    @Async
    public void notifyOrder(Order order, String templateId) {

        UserProfile customer = userProfileService.find(order.getCustomerId());
        StoreProfile store = storeService.find(order.getShopId());
        List<UserProfile> admins = userProfileService.findByRole(ProfileRoles.ADMIN);
        Email email = new Email();
        email.setFrom("iZinga", "lindani@izinga.store");
        admins.forEach(admin -> email.addRecipient(admin.getName(), admin.getEmailAddress()));
        email.setTemplateId(templateId);

        // you can use the addVariable overload to add a variable to all recipients
        email.AddVariable("store.name", store.getName());
        email.AddVariable("order.order_number", order.getId());
        email.AddVariable("order.date", order.getModifiedDate().toString());
        email.AddVariable("invoice.subtotal", "" + order.getBasketAmount());
        email.AddVariable("invoice.pay_method", order.getPaymentType().toString());
        email.AddVariable("invoice.total", "" + order.getTotalAmount());
        email.AddVariable("order.customer_message", order.getShippingData().getAdditionalInstructions());
        email.AddVariable("customer.name", customer.getName());
        email.AddVariable("order.billing_address", order.getShippingData().getToAddress());
        email.AddVariable("customer.phone", customer.getMobileNumber());
        email.AddVariable("customer.email", customer.getEmailAddress());

        MailerSend ms = new MailerSend();
        ms.setToken(apiKey);

        try {
            ms.emails().send(email);
        } catch (MailerSendException e) {
            e.printStackTrace();
        }
    }
}
