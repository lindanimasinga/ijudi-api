package io.curiousoft.izinga.messaging.firebase;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class FirebaseNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseNotificationService.class);
    private static final Gson GSON = new GsonBuilder().create();

    public static final String TOPICS = "/topics/";

    private final FirebaseConnectionWrapper firebaseConnectionWrapper;
    private final DeviceRepository deviceRepository;

    public FirebaseNotificationService(FirebaseConnectionWrapper firebaseConnectionWrapper, DeviceRepository deviceRepo) {
        this.firebaseConnectionWrapper = firebaseConnectionWrapper;
        this.deviceRepository = deviceRepo;
    }

    @Async
    public Map sendNotification(Device device, PushMessage message) throws Exception {
        FCMMessage fcmMessage = getFcmMessage(device.getToken(), message);
        LOGGER.debug(GSON.toJson(fcmMessage));
        try {
            return firebaseConnectionWrapper.sendMessage(fcmMessage);
        } catch (FirebaseTokenNotFound ex) {
            deviceRepository.delete(device);
            LOGGER.info("Removed expired device " + device.getToken());
            return null;
        }
    }

    public void sendNotifications(List<Device> device, PushMessage message) {
        device.forEach(d -> {
            try {
                Map response = sendNotification(d, message);
                if(response.get("results") != null && isDeviceNoLongerRegistered(response.get("results").toString())) {
                    deviceRepository.delete(d);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private boolean isDeviceNoLongerRegistered(String firebaseResults) {
        return firebaseResults != null && firebaseResults.contains("NotRegistered");
    }

    @Async
    public void notifyStoreOrderPlaced(String storeName, List<Device> devices, Order order) {
        devices.forEach(device -> {
            PushHeading title = new PushHeading(
                    "New order placed at "+ storeName +". Please confirm the order.",
                    "New Order Received", null,
                    String.format("https://onboard.izinga.co.za/business/info/%s/order/%s", order.getShopId(), order.getId()));
            PushMessage message = new PushMessage(PushMessageType.NEW_ORDER, title, order);
            try {
                sendNotification(device, message);
                LOGGER.info("Notification sent to device " + device.getUserId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void registerDevice(Device device) {
    }

    public void deleteDevice(Device oldDevice) {
        //todo remove using instance id api
    }

    public String createTopic(String name, String deviceToken) throws Exception {
        String topic = firebaseConnectionWrapper.createTopic(name, deviceToken);
        firebaseConnectionWrapper.unSubscribe(TOPICS + topic, deviceToken);
        return topic;
    }

    public void subscribeTopic(String topicName, String protocol, String deviceToken) throws Exception {
        firebaseConnectionWrapper.subscribeTopic(topicName, deviceToken);
    }

    public void publishTopic(String pushNotificationChannel, PushMessage message) throws Exception {

        FCMMessage fcmMessage = getFcmMessage(TOPICS + pushNotificationChannel, message);
        firebaseConnectionWrapper.sendMessage(fcmMessage);
    }

    private FCMMessage getFcmMessage(String destination, PushMessage message) {
        FCMNotification notification = new FCMNotification(message.getPushHeading().getBody(),
                message.getPushHeading().getTitle(),
                null);
        WebPush webPush = new WebPush(new WebPush.FcmOptions(message.getPushHeading().getLink()));
        return new FCMMessage(destination, notification, webPush);
    }

    public List<String> getAllTopics() {
        return firebaseConnectionWrapper.getAllTopics();
    }

    public void deleteTopics(List<String> topics) {
        firebaseConnectionWrapper.getTopics(topics);
    }

    public List<String> getSubscriptionsForTopics(String topicName) {
        return firebaseConnectionWrapper.getSubscriptionsForTopics(topicName);
    }

    public void unSubscribe(List<String> subscriptions) {
    }

    public void unSubscribe(String deviceToken, String topic) throws Exception {
        if(!StringUtils.isEmpty(deviceToken) && !StringUtils.isEmpty(topic)) {
            topic = !topic.startsWith(TOPICS) ? TOPICS + topic : topic;
            firebaseConnectionWrapper.unSubscribe(topic, deviceToken);
        }
    }

    @Async
    public void notifyMessengerOrderPlaced(List<Device> messengerDevices,
                                           Order order,
                                           StoreProfile shop) {
        messengerDevices.forEach(device -> {
            PushHeading title = new PushHeading(
                    "An order is placed at %s shop. We will notify you when the order is ready for collection. :)"
                            .formatted(shop.getName()),
                    PushMessageType.NEW_ORDER.toString(),
                    null,
                    String.format("https://onboard.izinga.co.za/business/info/%s/order/%s", order.getShopId(), order.getId()));
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