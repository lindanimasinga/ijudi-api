package io.curiousoft.izinga.messaging;

import io.curiousoft.izinga.messaging.firebase.FirebaseConnectionWrapper;
import io.curiousoft.izinga.messaging.zoomconnectsms.ZoomSMSFactory;
import io.curiousoft.izinga.messaging.zoomconnectsms.ZoomSMSService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class MessagingConfig {

    @Bean
    public FirebaseConnectionWrapper createFirebaseMessager(@Value("${google.api.key}") String apikey)
            throws IOException {
        return new FirebaseConnectionWrapper(apikey);
    }

    @Bean
    public ZoomSMSService createSmsService(@Value("${zoomconnectsms.api.email}") String email,
                                           @Value("${zoomconnectsms.api.key}") String token) {
        return ZoomSMSFactory.createZoomSMSService(email, token);
    }
}
