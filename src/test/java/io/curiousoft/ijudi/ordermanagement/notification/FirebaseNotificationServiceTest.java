package io.curiousoft.ijudi.ordermanagement.notification;

import static org.junit.Assert.*;

import com.curiousoft.google.services.FCMMessage;
import com.curiousoft.google.services.FirebaseConnectionWrapper;
import io.curiousoft.ijudi.ordermanagement.model.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FirebaseNotificationServiceTest {

    private FirebaseNotificationService firebaseNotificationService;
    @Mock
    private FirebaseConnectionWrapper wrapper;

    @Before
    public void setUp() throws Exception {
        firebaseNotificationService = new FirebaseNotificationService(wrapper);
    }

    @Test
    public void testSendNotification() throws Exception {
        //given
        Device device = new Device("testToken");
        String content = "hello world";
        PushHeading heading = new PushHeading("hello", "greetings", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER, heading, content);

        Map data = new HashMap<>();
        data.put("messageType", message.getPushMessageType());
        data.put("messageContent", message.getPushContent());
        FCMMessage fcmMessage = new FCMMessage(device.getToken(), null, data);

        //when
        firebaseNotificationService.sendNotification(device, message);
        //then
        verify(wrapper).sendMessage(fcmMessage);
    }

    @Test
    public void orderPlacedNotify() throws Exception {
        //given

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY,
                10);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.INSTORE);
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setShopPaid(true);
        order.setShopId("shopId");
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");

        Device device = new Device("testToken");
        Device device2 = new Device("testToken");
        ArrayList<Device> devices = new ArrayList<>();
        devices.add(device);
        devices.add(device2);

        //when
        firebaseNotificationService.notifyOrderPlaced(devices, order);
        //then
        verify(wrapper, times(2)).sendMessage(any(FCMMessage.class));
    }

    @Test
    public void testRegisterDevice() throws Exception {
        //no registration required for FCM
    }

    @Test
    public void testDeleteDevice() throws Exception {
        //no deletion required for FCM
    }

    @Test
    public void testCreateTopic() throws Exception {

        //given
        String name = "test";
        String auditToken = "myToken id";
        //when
        when(wrapper.createTopic(name, auditToken)).thenReturn(name);
        String topicName = firebaseNotificationService.createTopic(name, auditToken);

        verify(wrapper).createTopic(name, auditToken);
        verify(wrapper).unSubscribe("/topics/" +name, auditToken);
        Assert.assertEquals(name, topicName);
    }

    @Test
    public void testSubscribeTopic() throws Exception {
        //given
        String name = "test";
        String auditToken = "myToken id";
        //when
        firebaseNotificationService.subscribeTopic(name, null, auditToken);

        verify(wrapper).subscribeTopic(name, auditToken);
    }

    @Test
    public void testPublishTopic() throws Exception {

        //given
        String content = "hello world";
        String topicName = "topic";

        PushHeading heading = new PushHeading("hello", "greetings", null);
        PushMessage message = new PushMessage(PushMessageType.MARKETING, heading, content);

        Map data = new HashMap<>();
        data.put("messageType", message.getPushMessageType());
        data.put("messageContent", message.getPushContent());
        FCMMessage fcmMessage = new FCMMessage("/topics/"+topicName, null, data);

        //when
        firebaseNotificationService.publishTopic(topicName, message);
        //then
        verify(wrapper).sendMessage(fcmMessage);
    }
}