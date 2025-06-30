package io.curiousoft.izinga.messaging.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FirebaseConnectionWrapper {

    private static Logger LOGGER = LoggerFactory.getLogger(FirebaseConnectionWrapper.class);

    private GoogleCredentials credentials;
    private String projectId;
    private GoogleServices.FirebaseInstanceIdHttpService firebaseHttpService;
    private GoogleServices.FirebaseMessageService firebaseMessagingHttpService;

    private FirebaseConnectionWrapper() {
        firebaseHttpService = GoogleServices.getInstanceIdService();
        firebaseMessagingHttpService = GoogleServices.getMessagingService();
    }

    public FirebaseConnectionWrapper(GoogleCredentials credentials, String projectId) {
        this();
        this.credentials = credentials;
        this.projectId = projectId;
    }

    public FirebaseConnectionWrapper(GoogleCredentials credentials, String projectId, GoogleServices.FirebaseInstanceIdHttpService firebaseHttpService,
                                     GoogleServices.FirebaseMessageService firebaseMessageService) {
        this.firebaseHttpService = firebaseHttpService;
        this.firebaseMessagingHttpService = firebaseMessageService;
        this.credentials = credentials;
        this.projectId = projectId;
    }

    public Map sendMessage(FCMMessage fcmMessage) throws Exception {
        credentials.refreshIfExpired();
        var token = "Bearer %s".formatted(credentials.getAccessToken().getTokenValue());
        Call<Map> requestCall = firebaseMessagingHttpService.sendMessage(token, projectId, fcmMessage);
        Map response = processRequest(requestCall);
        LOGGER.info("Message sent to {}", fcmMessage.getMessage().token());
        LOGGER.info("Message notification {}", fcmMessage.getMessage().notification());
        return response;
    }

    public String createTopic(String topicName, String deviceToken) throws Exception {
        credentials.refreshIfExpired();
        var token = "Bearer %s".formatted(credentials.getAccessToken().getTokenValue());
        Call<Map> requestCall = firebaseHttpService.createTopic(token, deviceToken, topicName);
        processRequest(requestCall);
        return topicName;
    }

    private <T> T processRequest(Call<T> requestCall) throws Exception {
        LOGGER.info("FCM Request {}", requestCall.request().toString());
        Response<T> response = requestCall.execute();
        if (response.isSuccessful()) {
            LOGGER.info("FCM Response success! code: {} body: {}",  response.code(), response.body());
            return response.body();
        }
        LOGGER.error("FCM response code: {} Body {}", response.code(), response.errorBody().string());
        if (response.code() == 404) {
            throw new FirebaseTokenNotFound(response.code());
        }
        throw new Exception(response.errorBody().string());
    }

    public void subscribeTopic(String topicName, String deviceToken) throws Exception {
        credentials.refreshIfExpired();
        var token = "Bearer %s".formatted(credentials.getAccessToken().getTokenValue());
        Call<Map> requestCall = firebaseHttpService.subscribeTopic(token, deviceToken, topicName);
        processRequest(requestCall);
    }

    public List<String> getAllTopics() {
        return null;
    }

    public void getTopics(List<String> topics) {

    }

    public List<String> getSubscriptionsForTopics(String topicName) {
        return null;
    }

    public void unSubscribe(String name, String deviceToken) throws Exception {
        credentials.refreshIfExpired();
        var token = "Bearer %s".formatted(credentials.getAccessToken().getTokenValue());
        FCMUnSubscribeMessage message = new FCMUnSubscribeMessage(name, Collections.singletonList(deviceToken));
        Call<Map> requestCall = firebaseHttpService.unSubscribeTopic(token, message);
        processRequest(requestCall);
    }

    public GoogleServices.FirebaseInstanceIdHttpService getFirebaseHttpService() {
        return firebaseHttpService;
    }

    public void setFirebaseHttpService(GoogleServices.FirebaseInstanceIdHttpService firebaseHttpService) {
        this.firebaseHttpService = firebaseHttpService;
    }

    public GoogleServices.FirebaseMessageService getFirebaseMessagingHttpService() {
        return firebaseMessagingHttpService;
    }

    public void setFirebaseMessagingHttpService(GoogleServices.FirebaseMessageService firebaseMessagingHttpService) {
        this.firebaseMessagingHttpService = firebaseMessagingHttpService;
    }
}
