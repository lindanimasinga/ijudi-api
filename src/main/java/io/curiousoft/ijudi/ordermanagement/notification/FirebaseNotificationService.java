package io.curiousoft.ijudi.ordermanagement.notification;


import com.curiousoft.google.services.FCMMessage;
import com.curiousoft.google.services.FirebaseConnectionWrapper;
import io.curiousoft.ijudi.ordermanagement.model.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FirebaseNotificationService implements PushNotificationService {

    public static final String TOPICS = "/topics/";

    private final FirebaseConnectionWrapper firebaseConnectionWrapper;

    public FirebaseNotificationService(FirebaseConnectionWrapper firebaseConnectionWrapper) {
        this.firebaseConnectionWrapper = firebaseConnectionWrapper;
    }

    @Async
    @Override
    public void sendNotification(Device device, PushMessage message) throws Exception {
        FCMMessage fcmMessage = getFcmMessage(device.getToken(), message);
        firebaseConnectionWrapper.sendMessage(fcmMessage);
    }

    @Async
    @Override
    public void notifyStoreOrderPlaced(List<Device> devices, Order order) {
        devices.forEach(device -> {
            PushHeading title = new PushHeading("New " + order.getOrderType().toString() + " Order",
                    PushMessageType.NEW_ORDER.toString(), null);
            PushMessage message = new PushMessage(PushMessageType.NEW_ORDER, title, order);
            try {
                sendNotification(device, message);
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
        return new FCMMessage(destination, null, data);
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
            PushHeading title = new PushHeading("New place at " +shop.getName()+ " Order. we will notify when an order is ready for collection.",
                    PushMessageType.NEW_ORDER.toString(), null);
            PushMessage message = new PushMessage(PushMessageType.NEW_ORDER, title, order);
            try {
                sendNotification(device, message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
