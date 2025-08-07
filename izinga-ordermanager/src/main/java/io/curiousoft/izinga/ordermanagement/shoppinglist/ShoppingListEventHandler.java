package io.curiousoft.izinga.ordermanagement.shoppinglist;

import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.service.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Service
public class ShoppingListEventHandler {

    private final EmailNotificationService emailNotificationService;
    private final WhatsappNotificationService whatsappNotificationService;
    private final UserProfileService userProfileService;
    private final StoreService storeService;

    public ShoppingListEventHandler(EmailNotificationService emailNotificationService,
                             WhatsappNotificationService whatsappNotificationService,
                             UserProfileService userProfileService, StoreService storeService) {
        this.emailNotificationService = emailNotificationService;
        this.whatsappNotificationService = whatsappNotificationService;
        this.userProfileService = userProfileService;
        this.storeService = storeService;
    }

    @Async
    @EventListener
    public void handle(ShoppingListRunEvent shoppingListRunEvent) throws IOException {
        var shoppingList = shoppingListRunEvent.getShoppingList();
        var shop = storeService.find(shoppingList.getShopId());
        shoppingList.getUserIds().forEach(userId -> {
            var customer = userProfileService.find(userId);
            emailNotificationService.sendShoppingListRunNotification(customer, shoppingList);
            try {
                whatsappNotificationService.notifyShoppingListRun(customer, shop, shoppingList);
            } catch (IOException e) {
                log.error("Failed to send shopping list run notification via WhatsApp for user {}: {}", userId, e.getMessage());
            }
        });
    }

    public EmailNotificationService emailNotificationService() {
        return emailNotificationService;
    }

    public WhatsappNotificationService whatsappNotificationService() {
        return whatsappNotificationService;
    }

    public UserProfileService userProfileService() {
        return userProfileService;
    }

    public StoreService storeService() {
        return storeService;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ShoppingListEventHandler) obj;
        return Objects.equals(this.emailNotificationService, that.emailNotificationService) &&
                Objects.equals(this.whatsappNotificationService, that.whatsappNotificationService) &&
                Objects.equals(this.userProfileService, that.userProfileService) &&
                Objects.equals(this.storeService, that.storeService);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emailNotificationService, whatsappNotificationService, userProfileService, storeService);
    }

    @Override
    public String toString() {
        return "ShoppingListEventHandler[" +
                "emailNotificationService=" + emailNotificationService + ", " +
                "whatsappNotificationService=" + whatsappNotificationService + ", " +
                "userProfileService=" + userProfileService + ", " +
                "storeService=" + storeService + ']';
    }


}
