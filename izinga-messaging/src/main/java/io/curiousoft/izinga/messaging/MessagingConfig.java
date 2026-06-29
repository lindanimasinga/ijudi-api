package io.curiousoft.izinga.messaging;

import com.google.auth.oauth2.GoogleCredentials;
import io.curiousoft.izinga.messaging.firebase.FirebaseConnectionWrapper;
import io.curiousoft.izinga.messaging.firebase.FirebaseAuthConfig;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppFactory;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppService;
import io.curiousoft.izinga.messaging.zoomconnectsms.ZoomSMSFactory;
import io.curiousoft.izinga.messaging.zoomconnectsms.ZoomSMSService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Configuration
public class MessagingConfig {

    private static final String MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    @Bean
    public FirebaseConnectionWrapper createFirebaseMessager(FirebaseAuthConfig firebaseAuthConfig)
            throws IOException {

        var firebaseKeyStream = new ByteArrayInputStream(firebaseAuthConfig.configAsJson().getBytes());
        var credentials = GoogleCredentials.fromStream(firebaseKeyStream)
                .createScoped(List.of(MESSAGING_SCOPE));
        return new FirebaseConnectionWrapper(credentials, firebaseAuthConfig.projectId());
    }

    @Bean
    public ZoomSMSService createSmsService(@Value("${zoomconnectsms.api.email}") String email,
                                           @Value("${zoomconnectsms.api.key}") String token) {
        return ZoomSMSFactory.createZoomSMSService(email, token);
    }

    @Bean
    public WhatsAppService createWhatsappService(@Value("${whatsapp.cloud.api.key}") String token) {
        return WhatsAppFactory.createWhatsappService(token);
    }
}
