package io.curiousoft.izinga.ordermanagement.notification;


import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.PushMessage;
import io.curiousoft.izinga.commons.model.StoreProfile;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Map;


public interface PushNotificationService {

    Map sendNotification(Device device, PushMessage message) throws Exception;

    void sendNotifications(List<Device> device, PushMessage message);

    @Async
    void notifyStoreOrderPlaced(String storeName, List<Device> devices, Order order);

    void registerDevice(Device device);

    void deleteDevice(Device oldDevice);

    String createTopic(String name, String deviceRegistrationId) throws Exception;

    void subscribeTopic(String topicArn, String protocol, String endpointArn) throws Exception;

    void publishTopic(String pushNotificationChannel, PushMessage message) throws Exception;

    List<String> getAllTopics();

    void deleteTopics(List<String> topics);

    List<String> getSubscriptionsForTopics(String topicName);

    void unSubscribe(List<String> subscriptions);

    void unSubscribe(String deviceToken, String topic) throws Exception;

    @Async
    void notifyMessengerOrderPlaced(List<Device> messengerDevices, Order order,
                                    StoreProfile shop);
}
