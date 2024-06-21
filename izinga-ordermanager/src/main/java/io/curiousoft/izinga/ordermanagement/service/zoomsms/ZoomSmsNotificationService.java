package io.curiousoft.izinga.ordermanagement.service.zoomsms;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static java.lang.String.format;

@Service
public class ZoomSmsNotificationService implements AdminOnlyNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZoomSmsNotificationService.class);

    private final String url;
    private final String email;
    private final String token;
    private final RestTemplate rest;

    public ZoomSmsNotificationService(@Value("${zoomconnectsms.api.endpoint}") String url,
                                          @Value("${zoomconnectsms.api.email}") String email,
                                          @Value("${zoomconnectsms.api.key}") String token) {
        this.url = url;
        this.email = email;
        this.token = token;
        this.rest = new RestTemplateBuilder().build();
    }

    @Override
    public void sendMessage(String mobileNumber, String message) {
        LOGGER.info(mobileNumber.formatted("Sending sms to %s"));
        ZoomSMSMessage smsMessage = new ZoomSMSMessage(mobileNumber, message);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        //Create a new HttpEntity
        ResponseEntity<ZoomResponse> response = rest.postForEntity("%s?email=%s&token=%s".formatted(url, email, token),
                smsMessage, ZoomResponse.class);

        if(response.getStatusCodeValue() != 200) {
            LOGGER.error("Unable to send sms. {}", response.getBody());
        }
    }

    @Override
    public void notifyOrderPlaced(StoreProfile store, Order order, UserProfile userProfile) {
        String shopMessage = format("Hello %s. You have received a new order. Please open iZinga app and confirm.", store.getName());
        String customerMessage = format("Hello %s. Your has been received. Please open iZinga app for status updates.", userProfile.getName());
        if(store.getOrderUrl() != null) {
            shopMessage = format("Hello %s. You have received a new order from %s. %s", store.getName(),userProfile.getName(), store.getOrderUrl() + order.getId());
            customerMessage = format("Hello %s. Your order has been received. For status updates. Visit %s. From %s", userProfile.getName() , store.getOrderUrl() + order.getId(), store.getName());
        }
        sendMessage(store.getMobileNumber(), shopMessage);
        sendMessage(userProfile.getMobileNumber(), customerMessage);
    }

    @Override
    public void notifyShopOrderPlaced(StoreProfile store, Order order, UserProfile userProfile) {
        String shopMessage = format("Hello %s. You have received a new order. Please open iZinga app and confirm.", store.getName());
        if(store.getOrderUrl() != null) {
            shopMessage = format("Hello %s. You have received a new order from %s. %s", store.getName(),userProfile.getName(), store.getOrderUrl() + order.getId());
        }
        sendMessage(store.getMobileNumber(), shopMessage);
    }
}
