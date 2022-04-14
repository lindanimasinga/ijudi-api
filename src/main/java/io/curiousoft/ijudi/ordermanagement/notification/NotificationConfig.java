package io.curiousoft.ijudi.ordermanagement.notification;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.curiousoft.alarmsystem.amazon.tools.AmazonSNSClientWrapper;
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

    @Bean
    public AmazonSNSClientWrapper createAmazonClientWrapper(@Value("${amz.accessKey}") String amzAccessKey,
                                                            @Value("${amz.secretKey}") String amzApiKey) {
        AmazonSNSClient amazonClient = new AmazonSNSClient(new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return amzAccessKey;
            }

            @Override
            public String getAWSSecretKey() {
                return amzApiKey;
            }
        });
        return new AmazonSNSClientWrapper(amazonClient);
    }
}
