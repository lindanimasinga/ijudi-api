package io.curiousoft.izinga.ordermanagement.notification;

import com.curiousoft.alarmsystem.messaging.firebase.FCMMessage;
import com.curiousoft.alarmsystem.messaging.firebase.FCMNotification;
import com.curiousoft.alarmsystem.messaging.firebase.FirebaseConnectionWrapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.curiousoft.izinga.commons.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FirebaseNotificationService implements PushNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseNotificationService.class);
    private static final Gson GSON = new GsonBuilder().create();

    public static final String TOPICS = "/topics/";

    private final FirebaseConnectionWrapper firebaseConnectionWrapper;

    public FirebaseNotificationService(FirebaseConnectionWrapper firebaseConnectionWrapper) {
        this.firebaseConnectionWrapper = firebaseConnectionWrapper;
    }

    @Async
    @Override
    public void sendNotification(Device device, PushMessage message) throws Exception {
        FCMMessage fcmMessage = getFcmMessage(device.getToken(), message);
        LOGGER.debug(GSON.toJson(fcmMessage));
        firebaseConnectionWrapper.sendMessage(fcmMessage);
    }

    @Async
    @Override
    public void notifyStoreOrderPlaced(String storeName, List<Device> devices, Order order) {
        devices.forEach(device -> {
            PushHeading title = new PushHeading(
                    "New order placed at "+ storeName +". Please confirm the order.",
                    "New Order Received", null);
            PushMessage message = new PushMessage(PushMessageType.NEW_ORDER, title, order);
            try {
                sendNotification(device, message);
                LOGGER.info("Notification sent to device " + device.getUserId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void registerDevice(Device device) {
    }

    @Override
    public void deleteDevice(Device oldDevice) {
        //todo remove using instance id api
    }

    @Override
    public String createTopic(String name, String deviceToken) throws Exception {
        String topic = firebaseConnectionWrapper.createTopic(name, deviceToken);
        firebaseConnectionWrapper.unSubscribe(TOPICS + topic, deviceToken);
        return topic;
    }

    @Override
    public void subscribeTopic(String topicName, String protocol, String deviceToken) throws Exception {
        firebaseConnectionWrapper.subscribeTopic(topicName, deviceToken);
    }

    @Override
    public void publishTopic(String pushNotificationChannel, PushMessage message) throws Exception {

        FCMMessage fcmMessage = getFcmMessage(TOPICS + pushNotificationChannel, message);
        firebaseConnectionWrapper.sendMessage(fcmMessage);
    }

    private FCMMessage getFcmMessage(String destination, PushMessage message) {
        Map data = new HashMap<>();
        data.put("messageType", message.getPushMessageType());
        data.put("messageContent", message.getPushContent());

        FCMNotification notification = new FCMNotification(message.getPushHeading().getBody(),
                message.getPushHeading().getTitle(),
                null);
        return new FCMMessage(destination, notification, data);
    }

    @Override
    public List<String> getAllTopics() {
        return firebaseConnectionWrapper.getAllTopics();
    }

    @Override
    public void deleteTopics(List<String> topics) {
        firebaseConnectionWrapper.getTopics(topics);
    }

    @Override
    public List<String> getSubscriptionsForTopics(String topicName) {
        return firebaseConnectionWrapper.getSubscriptionsForTopics(topicName);
    }

    @Override
    public void unSubscribe(List<String> subscriptions) {
    }

    @Override
    public void unSubscribe(String deviceToken, String topic) throws Exception {
        if(!StringUtils.isEmpty(deviceToken) && !StringUtils.isEmpty(topic)) {
            topic = !topic.startsWith(TOPICS) ? TOPICS + topic : topic;
            firebaseConnectionWrapper.unSubscribe(topic, deviceToken);
        }
    }

    @Async
    @Override
    public void notifyMessengerOrderPlaced(List<Device> messengerDevices,
                                           Order order,
                                           StoreProfile shop) {
        messengerDevices.forEach(device -> {
            PushHeading title = new PushHeading("An order is placed at " +shop.getName() + " shop. We will notify you when the order is ready for collection. :)",
                    PushMessageType.NEW_ORDER.toString(), null);
            Order tempOrder = new Order();
            PushMessage message = new PushMessage(PushMessageType.NEW_ORDER, title, tempOrder);
            try {
                sendNotification(device, message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}