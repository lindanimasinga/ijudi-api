package io.curiousoft.ijudi.ordermanagement.notification;

import com.curiousoft.alarmsystem.messaging.firebase.FirebaseConnectionWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class NotificationConfig {

    @Bean
    public FirebaseConnectionWrapper createFirebaseMessager(@Value("${google.api.key}") String apikey)
            throws IOException {
        return new FirebaseConnectionWrapper(apikey);
    }
}
