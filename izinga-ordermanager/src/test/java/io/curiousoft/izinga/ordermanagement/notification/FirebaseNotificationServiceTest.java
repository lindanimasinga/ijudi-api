package io.curiousoft.izinga.ordermanagement.notification;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.messaging.firebase.FCMMessage;
import io.curiousoft.izinga.messaging.firebase.FCMNotification;
import io.curiousoft.izinga.messaging.firebase.FirebaseConnectionWrapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FirebaseNotificationServiceTest {

    private io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService firebaseNotificationService;
    @Mock
    private FirebaseConnectionWrapper wrapper;
    @Mock
    DeviceRepository deviceRepo;

    @Before
    public void setUp() throws Exception {
        firebaseNotificationService = new io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService(wrapper, deviceRepo);
    }

    @Test
    public void testSendNotification() throws Exception {
        //given
        Device device = new Device("testToken");
        String content = "hello world";
        PushHeading heading = new PushHeading("hello", "greetings", null, null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER, heading, content);

        FCMNotification notification = new FCMNotification(heading.getBody(), heading.getTitle(), null);
        FCMMessage fcmMessage = new FCMMessage(device.getToken(), notification, null);

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

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
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
        firebaseNotificationService.notifyStoreOrderPlaced("ShopName", devices, order);
        //then
        verify(wrapper, times(2)).sendMessage(any(FCMMessage.class));
    }

    @Test
    public void notifyMessengerOrderPlaced() throws Exception {
        //given

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
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

        StoreProfile shop = new StoreProfile(
                StoreType.FOOD,
                "name",
                "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                null,
                "ownerId",
                new Bank());

        //when
        firebaseNotificationService.notifyMessengerOrderPlaced(devices, order, shop);
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

        PushHeading heading = new PushHeading("hello", "greetings", null, null);
        PushMessage message = new PushMessage(PushMessageType.MARKETING, heading, content);

        FCMNotification notification = new FCMNotification(heading.getBody(), heading.getTitle(), null);
        FCMMessage fcmMessage = new FCMMessage("/topics/"+topicName, notification, null);

        //when
        firebaseNotificationService.publishTopic(topicName, message);
        //then
        verify(wrapper).sendMessage(fcmMessage);
    }
}