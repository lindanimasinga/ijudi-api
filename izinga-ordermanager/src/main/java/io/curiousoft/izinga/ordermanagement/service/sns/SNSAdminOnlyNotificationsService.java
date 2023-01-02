package io.curiousoft.izinga.ordermanagement.service.sns;

import com.curiousoft.alarmsystem.amazon.tools.AmazonSNSClientWrapper;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import org.springframework.beans.factory.annotation.Autowired;

import static java.lang.String.format;

public class SNSAdminOnlyNotificationsService implements AdminOnlyNotificationService {

    AmazonSNSClientWrapper amazonSNSClientWrapper;

    @Autowired
    public SNSAdminOnlyNotificationsService(AmazonSNSClientWrapper amazonSNSClientWrapper) {
        this.amazonSNSClientWrapper = amazonSNSClientWrapper;
    }

    @Override
    public void sendMessage(String topic, String message) throws Exception {

    }

    @Override
    public void notifyOrderPlaced(StoreProfile store, Order persistedOrder, UserProfile userProfile) throws Exception {
        String shopMessage = format("Hello %s. You have received a new order. Please open iZinga app and confirm.", store.getName());
        String customerMessage = format("Hello %s. Your has been received. Please open iZinga app for status updates.", userProfile.getName());
        if(store.getOrderUrl() != null) {
            shopMessage = format("Hello %s. You have received a new order from %s. %s", store.getName(),userProfile.getName(), store.getOrderUrl() + persistedOrder.getId());
            customerMessage = format("Hello %s. Your order has been received. For status updates. Visit %s. From %s", userProfile.getName() , store.getOrderUrl() + persistedOrder.getId(), store.getName());
        }
        sendMessage(store.getMobileNumber(), shopMessage);
        sendMessage(userProfile.getMobileNumber(), customerMessage);
    }
}
