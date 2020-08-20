package io.curiousoft.ijudi.ordermanagement.service.zoomsms;

import io.curiousoft.ijudi.ordermanagement.service.SmsNotificationService;
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

@Service
public class ZoomSmsNotificationService implements SmsNotificationService {

    private static Logger LOGGER = LoggerFactory.getLogger(ZoomSmsNotificationService.class);

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
    public void sendMessage(String mobileNumber, String message) throws Exception {
        URI uri = new URI(url + "?email="+ email + "&token=" + token);
        LOGGER.info("Sending sms to "+ mobileNumber);
        ZoomSMSMessage smsMessage = new ZoomSMSMessage(mobileNumber, message);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        //Create a new HttpEntity
        final HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<ZoomResponse> response = rest.postForEntity(uri,
                smsMessage, ZoomResponse.class);

        if(response.getStatusCodeValue() != 200) {
            throw new Exception(String.valueOf(response.getBody()));
        }
    }
}
