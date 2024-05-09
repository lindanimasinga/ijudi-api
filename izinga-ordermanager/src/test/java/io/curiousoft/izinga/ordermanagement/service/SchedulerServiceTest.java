package io.curiousoft.izinga.ordermanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.OrderRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentService;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static io.curiousoft.izinga.ordermanagement.service.order.OrderServiceTest.createStoreProfile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SchedulerServiceTest {

    @Mock
    private OrderRepository repo;
    @Mock
    private UserProfileRepo customerRepo;
    @Mock
    private StoreRepository storeRepo;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PushNotificationService pushNotificationService;
    @Mock
    private AdminOnlyNotificationService smsNotifcation;
    @Mock
    private EmailNotificationService emailNotificationService;
    @Mock
    private DeviceRepository deviceRepo;
    @Mock
    private PromotionService promotionService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AdminOnlyNotificationService adminOnlyNotificationService;

    //system under test
    private SchedulerService sut;

    @Spy
    List<String> phoneNumbers = Lists.list("08128155660", "0812815707");
    int cleaupMinutes = 5;

    @Before
    public void setUp() throws Exception {
        sut = new SchedulerService(
                orderRepository,
                storeRepo,
                customerRepo,
                paymentService,
                deviceRepo,
                pushNotificationService,
                adminOnlyNotificationService,
                emailNotificationService,
                promotionService,
                cleaupMinutes,
                phoneNumbers
        );
    }

    @Test
    public void cleanUnPaidOrders() {
        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);


        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId("shopid");
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);


        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        storeProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        storeProfile.setBank(bank);
        //when
        sut.cleanUnpaidOrders();
        //verify
        verify(repo).deleteByShopPaidAndStageAndModifiedDateBefore(eq(false), eq(OrderStage.STAGE_0_CUSTOMER_NOT_PAID),
                any(Date.class));
    }

    @Test
    public void checkNotAcceptedOrdersAndSMS_Sent_To_Shop() throws Exception {
        //order 1
        Order order = new Order();
        order.setId("12345");
        Basket basket = new Basket();
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(10).atZone(ZoneId.systemDefault()).toInstant());
        order.setModifiedDate(orderDate);
        order.setShopId("shopid");
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);


        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date pickUpTime = Date.from(LocalDateTime.now().minusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping2.setPickUpTime(pickUpTime);
        shipping.setMessengerId("messagerID");
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant());
        order2.setModifiedDate(orderDate2);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        storeProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        storeProfile.setBank(bank);

        //when
        when(repo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM)).thenReturn(orders);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));
        sut.checkUnconfirmedOrders();

        //verify
        verify(repo).findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        verify(smsNotifcation, times(1))
                .sendMessage(storeProfile.getMobileNumber(),
                        "Hello " + storeProfile.getName() + ", Please accept the order " + order.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
    }

    @Test
    public void checkNotAcceptedOrdersAndSendToAdmin() throws Exception {
        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);


        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setSmsSentToShop(true);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(10).atZone(ZoneId.systemDefault()).toInstant());
        order.setModifiedDate(orderDate);
        order.setShopId("shopid");
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);


        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date pickUpTime = Date.from(LocalDateTime.now().minusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping2.setPickUpTime(pickUpTime);
        shipping.setMessengerId("messagerID");
        order2.setShippingData(shipping2);
        order2.setSmsSentToShop(true);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(2).atZone(ZoneId.systemDefault()).toInstant());
        order2.setModifiedDate(orderDate2);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);

        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        storeProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        storeProfile.setBank(bank);

        //when
        when(repo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM)).thenReturn(orders);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));
        sut.checkUnconfirmedOrders();

        //verify
        verify(smsNotifcation, times(0))
                .sendMessage(storeProfile.getMobileNumber(),
                        "Hello " + storeProfile.getName() + ", Please accept the order " + order.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
        verify(emailNotificationService).notifyAdminNewOrder(order);
        verify(repo).findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        verify(repo).save(order);
    }

    @Test
    public void checkNotAcceptedOrdersAndAdmin_Already_Notified() throws Exception {
        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);


        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setSmsSentToShop(true);
        order.setSmsSentToAdmin(true);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(10).atZone(ZoneId.systemDefault()).toInstant());
        order.setModifiedDate(orderDate);
        order.setShopId("shopid");
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);


        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date pickUpTime = Date.from(LocalDateTime.now().minusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping2.setPickUpTime(pickUpTime);
        shipping.setMessengerId("messagerID");
        order2.setShippingData(shipping2);
        order2.setSmsSentToShop(true);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(2).atZone(ZoneId.systemDefault()).toInstant());
        order2.setModifiedDate(orderDate2);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);

        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        storeProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        storeProfile.setBank(bank);
        //when
        when(repo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM)).thenReturn(orders);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));
        sut.checkUnconfirmedOrders();
        //verify
        verify(repo).findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        verify(smsNotifcation, times(0))
                .sendMessage(storeProfile.getMobileNumber(),
                        "Hello " + storeProfile.getName() + ", Please accept the order " + order.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
        verify(smsNotifcation, times(0))
                .sendMessage(phoneNumbers.get(0), "Hi, iZinga Admin. " + storeProfile.getName() + ", has not accepted order " + order.getId() +
                        " on iZinga app, otherwise the order will be cancelled.");
    }

    @Test
    public void checkNotAcceptedOrdersAndNotNotify() throws Exception {
        //given
        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);


        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(9).atZone(ZoneId.systemDefault()).toInstant());
        order.setModifiedDate(orderDate);
        order.setShopId("shopid");
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);


        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date pickUpTime = Date.from(LocalDateTime.now().minusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping2.setPickUpTime(pickUpTime);
        shipping.setMessengerId("messagerID");
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant());
        order2.setModifiedDate(orderDate2);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);

        StoreProfile initialProfile = createStoreProfile(StoreType.FOOD);
        Date date = Date.from(LocalDateTime.now().plusDays(2).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);
        //when
        when(repo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM)).thenReturn(orders);
        sut.checkUnconfirmedOrders();
        //verify
        verify(repo).findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        verify(smsNotifcation, times(0))
                .sendMessage(initialProfile.getMobileNumber(),
                        "Hello " + initialProfile.getName() + ", Please accept the order " + order.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
    }

    @Test
    public void checkNotAcceptedCollectionOrdersAndNotify() throws Exception {
        //given
        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);


        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        shipping.setMessengerId("messagerID");
        Date pickUpTime = Date.from(LocalDateTime.now().plusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(pickUpTime);
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(66).atZone(ZoneId.systemDefault()).toInstant());
        order.setModifiedDate(orderDate);
        order.setShopId("shopid");
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);


        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        shipping.setMessengerId("messagerID");
        Date pickUpTime2 = Date.from(LocalDateTime.now()
                .plusMinutes(60)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        shipping2.setPickUpTime(pickUpTime2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(60).atZone(ZoneId.systemDefault()).toInstant());
        order2.setModifiedDate(orderDate2);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        StoreProfile initialProfile = createStoreProfile(StoreType.FOOD);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);
        //when
        when(repo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM)).thenReturn(orders);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(initialProfile));

        sut.checkUnconfirmedOrders();

        //verify
        verify(repo).findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Assert.assertTrue(order2.getSmsSentToShop());
        verify(repo).save(order2);
        verify(smsNotifcation, times(1))
                .sendMessage(initialProfile.getMobileNumber(),
                        "Hello " + initialProfile.getName() + ", Please accept the order " + order2.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
    }

    @Test
    public void checkNotAcceptedCollectionOrdersAnd_NotNotify() throws Exception {
        //given
        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);


        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        shipping.setMessengerId("messagerID");
        Date pickUpTime = Date.from(LocalDateTime.now().plusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(pickUpTime);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(66).atZone(ZoneId.systemDefault()).toInstant());
        order.setModifiedDate(orderDate);
        order.setShopId("shopid");
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);


        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        shipping.setMessengerId("messagerID");
        Date pickUpTime2 = Date.from(LocalDateTime.now()
                .plusMinutes(61)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        shipping2.setPickUpTime(pickUpTime2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(60).atZone(ZoneId.systemDefault()).toInstant());
        order2.setModifiedDate(orderDate2);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        storeProfile.setBank(bank);

        //when
        when(repo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM)).thenReturn(orders);
        sut.checkUnconfirmedOrders();
        //verify
        verify(repo).findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        verify(smsNotifcation, times(0))
                .sendMessage(storeProfile.getMobileNumber(),
                        "Hello " + storeProfile.getName() + ", Please accept the order " + order2.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
    }

    @Test
    public void getLatestPromotions_and_publish_two_to_customers() throws Exception {
        //give
        List<Promotion> promotions = new ObjectMapper().readValue(promosionsJson, new TypeReference<>() {});
        when(promotionService.finAllPromotions(StoreType.FOOD)).thenReturn(promotions);
        List<Order> orders = new ObjectMapper().readValue(allOrderJson, new TypeReference<>() {});;
        when(orderRepository.findAll()).thenReturn(orders);
        when(storeRepo.findById(any())).thenReturn(Optional.of(createStoreProfile(StoreType.FOOD, 1, 12)));
        String pushToken = "23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg";
        Device oldDevice = new Device(pushToken);
        oldDevice.setUserId("old user");

        List<Device> devices = Collections.nCopies( 20, oldDevice);
        when(deviceRepo.findByUserIdIn(any(List.class))).thenReturn(devices);

        //when
        sut.publishPromosOfTheDay();

        //then
        var messageArg = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationService, times(1)).sendNotifications(eq(devices), messageArg.capture());
        Assertions.assertEquals("Test: hello", messageArg.getValue().getPushHeading().getTitle());
        Assertions.assertEquals("world", messageArg.getValue().getPushHeading().getBody());
    }

    static String promosionsJson = """
            [{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=262509&ts=1697521357000",
              "shopId": "5e6e8243-22ea-4313-b439-998a0e78628e",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=266376&ts=1699338488000",
              "shopId": "27ebe134-2424-4b08-bd07-377c1d700f40",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=266389&ts=1699341708000",
              "shopId": "27ebe134-2424-4b08-bd07-377c1d700f40",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=262236&ts=1697092623000",
              "shopId": "420e5d2c-2252-440b-bedf-234d681a2a98",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=262230&ts=1697092623000",
              "shopId": "420e5d2c-2252-440b-bedf-234d681a2a98",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=262509&ts=1697521357000",
              "shopId": "5e6e8243-22ea-4313-b439-998a0e78628e",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=266389&ts=1699341708000",
              "shopId": "27ebe134-2424-4b08-bd07-377c1d700f40",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=262209&ts=1697092622000",
              "shopId": "420e5d2c-2252-440b-bedf-234d681a2a98",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=262235&ts=1697092623000",
              "shopId": "420e5d2c-2252-440b-bedf-234d681a2a98",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=213983&ts=1673333635000",
              "shopId": "d08206fb-a70c-4c8a-bf74-e2c5a2801057",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https:null",
              "shopId": "c80f7ce8-449f-4592-a3c8-fefdd2011d2d",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://static.yumbi.com/management/api/resource/?id=216651&ts=1676901637000",
              "shopId": "c80f7ce8-449f-4592-a3c8-fefdd2011d2d",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://images.ctfassets.net/0p42pznmbeec/RLbLuAJJHq256wzL9hj1N/521b7b4cf0d1c87d5535e0c1b68d8088/1200x900.png",
              "shopId": "fce9ad67-cc9e-4f43-915d-06d7c4a7127c",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            },{
              "imageUrl": "https://images.ctfassets.net/0p42pznmbeec/6dJhzFmsAKt07sI9YNwxXv/4a10f3b0260767182af9b091bdc83943/ABC2119-nugget_box_sf.jpg",
              "shopId": "fce9ad67-cc9e-4f43-915d-06d7c4a7127c",
              "shopType": "FOOD",
              "position": 10000,
              "title": "hello",
              "message": "world"
            }]
            """;

    static String allOrderJson = """
            [{
              "stage": "STAGE_3_READY_FOR_COLLECTION",
              "shippingData": {
                "fromAddress": "Ekasi Grillz",
                "toAddress": "25 Crescent Rd, Ottery, Cape Town, 7808, South Africa",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 8210,
                "messengerId": "ffd4c856-644f-4453-a5ed-84689801a747",
                "distance": 1641
              },
              "basket": {
                "items": [
                  {
                    "name": "Wrap (Chicken) & Chips",
                    "quantity": 1,
                    "price": 3.8809286885721086,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": [
                      {
                        "name": "Chips Sauce 1",
                        "values": [
                          "Chili",
                          "Mayonnaise",
                          "Tomato",
                          "None"
                        ],
                        "selected": "Mayonnaise",
                        "price": 0
                      },
                      {
                        "name": "Chips Sauce 2",
                        "values": [
                          "Chili",
                          "Mayonnaise",
                          "Tomato",
                          "None"
                        ],
                        "selected": "Tomato",
                        "price": 0
                      },
                      {
                        "name": "300ml cool drink",
                        "values": [
                          "Coke",
                          "Sprite",
                          "Stoney",
                          "None"
                        ],
                        "selected": "Sprite",
                        "price": 10
                      }
                    ]
                  }
                ]
              },
              "customerId": "a101c61c-9908-4ff1-b55c-48599876ea90",
              "shopId": "a3873c5f-9884-4d02-b712-00a70019e0d1",
              "description": "payfast-1637491290",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 533.9022,
              "messengerPaid": false,
              "smsSentToShop": false,
              "smsSentToAdmin": false,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_3_READY_FOR_COLLECTION",
              "shippingData": {
                "fromAddress": "KwaDladla",
                "toAddress": "1137 Ntombela Rd, Emakhosini, KwaMashu, 4360, South Africa",
                "buildingType": "APARTMENT",
                "unitNumber": "12",
                "buildingName": "MutMut",
                "type": "DELIVERY",
                "fee": 20,
                "messengerId": "ffd4c856-644f-4453-a5ed-84689801a747",
                "distance": 4
              },
              "basket": {
                "items": [
                  {
                    "name": "Stella Artois 330ml",
                    "quantity": 1,
                    "price": 3.2664995662192835,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": [
                      {
                        "name": "Stella Artois (6 x 330ml)",
                        "values": [
                          "Select",
                          "None"
                        ],
                        "selected": "None",
                        "price": 100
                      },
                      {
                        "name": "Stella Artois (12 x 330ml)",
                        "values": [
                          "Select",
                          "None"
                        ],
                        "selected": "Select",
                        "price": 200
                      },
                      {
                        "name": "Stella Artois (18 x 330ml)",
                        "values": [
                          "Select",
                          "None"
                        ],
                        "selected": "None",
                        "price": 300
                      },
                      {
                        "name": "Stella Artois  (24 x 330ml)",
                        "values": [
                          "Select",
                          "None"
                        ],
                        "selected": "None",
                        "price": 400
                      }
                    ]
                  }
                ]
              },
              "customerId": "e6cbb74a-b418-48d5-a32e-3b584cd5bc10",
              "shopId": "8efb069e-2a7b-44ad-8f01-7a5a75175cc1",
              "description": "payfast-1637586032",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 1.51255,
              "messengerPaid": false,
              "smsSentToShop": false,
              "smsSentToAdmin": false,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_3_READY_FOR_COLLECTION",
              "shippingData": {
                "fromAddress": "Ekasi Grillz",
                "toAddress": "1137 Ntombela Rd, Emakhosini, KwaMashu, 4360, South Africa",
                "buildingType": "OFFICE",
                "unitNumber": "9",
                "buildingName": "Mate",
                "type": "DELIVERY",
                "fee": 40,
                "messengerId": "ffd4c856-644f-4453-a5ed-84689801a747",
                "distance": 7
              },
              "basket": {
                "items": [
                  {
                    "name": "Chicken Wings & Chips",
                    "quantity": 1,
                    "price": 3.8809286885721086,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": [
                      {
                        "name": "Wings Sauce",
                        "values": [
                          "Hot",
                          "BBQ",
                          "None"
                        ],
                        "selected": "Hot",
                        "price": 0
                      },
                      {
                        "name": "Chips Sauce",
                        "values": [
                          "Chili",
                          "Mayonnaise",
                          "Tomato",
                          "None"
                        ],
                        "selected": "Chili",
                        "price": 0
                      },
                      {
                        "name": "300ml cool drink",
                        "values": [
                          "Coke",
                          "Sprite",
                          "Stoney",
                          "None"
                        ],
                        "selected": "Stoney",
                        "price": 10
                      }
                    ]
                  }
                ]
              },
              "customerId": "e6cbb74a-b418-48d5-a32e-3b584cd5bc10",
              "shopId": "a3873c5f-9884-4d02-b712-00a70019e0d1",
              "description": "payfast-1637588731",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 2.8522000000000003,
              "messengerPaid": false,
              "smsSentToShop": true,
              "smsSentToAdmin": true,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_3_READY_FOR_COLLECTION",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "57 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 15,
                "messengerId": "8058aaed-619b-4547-a727-dd68c84c01ac",
                "distance": 2
              },
              "basket": {
                "items": [
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 1,
                    "price": 44.9,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": []
                  },
                  {
                    "name": "Streetwise Two Chips",
                    "quantity": 1,
                    "price": 34.9,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": []
                  },
                  {
                    "name": "Krusher",
                    "quantity": 1,
                    "price": 29.9,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": [
                      {
                        "name": "Flavor",
                        "values": [
                          "Oreo",
                          "Verry Berry"
                        ],
                        "selected": "Oreo",
                        "price": 0
                      }
                    ]
                  },
                  {
                    "name": "9 Piece Bucket",
                    "quantity": 1,
                    "price": 124.9,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": []
                  }
                ]
              },
              "customerId": "b0ca0f9f-fbd1-474a-9f65-d6489f4a6aed",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "payfast-1637599385",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 16.224,
              "messengerPaid": false,
              "smsSentToShop": false,
              "smsSentToAdmin": false,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_1_WAITING_STORE_CONFIRM",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "57 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 15,
                "messengerId": "8058aaed-619b-4547-a727-dd68c84c01ac",
                "distance": 2
              },
              "basket": {
                "items": [
                  {
                    "name": "Wrapsta Box With Buddy Bottle",
                    "quantity": 1,
                    "price": 54.9,
                    "discountPerc": 0,
                    "storePrice": 0,
                    "options": [
                      {
                        "name": "How do you like you wrap",
                        "values": [
                          "Normal",
                          "Zinger"
                        ],
                        "selected": "Normal",
                        "price": 0
                      },
                      {
                        "name": "Buddy Bottle",
                        "values": [
                          "Coke",
                          "Sprite",
                          "Stoney"
                        ],
                        "selected": "Coke",
                        "price": 0
                      }
                    ]
                  },
                  {
                    "name": "Wrapsta Box With Buddy Bottle",
                    "quantity": 1,
                    "price": 54.9,
                    "discountPerc": 0,
                    "storePrice": 0,
                    "options": [
                      {
                        "name": "How do you like you wrap",
                        "values": [
                          "Normal",
                          "Zinger"
                        ],
                        "selected": "Zinger",
                        "price": 0
                      },
                      {
                        "name": "Buddy Bottle",
                        "values": [
                          "Coke",
                          "Sprite",
                          "Stoney"
                        ],
                        "selected": "Coke",
                        "price": 0
                      }
                    ]
                  }
                ]
              },
              "customerId": "a101c61c-9908-4ff1-b55c-48599876ea90",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "payfast-1637736539",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 8.112,
              "messengerPaid": false,
              "smsSentToShop": true,
              "smsSentToAdmin": true,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_2_STORE_PROCESSING",
              "shippingData": {
                "fromAddress": "McDonald Red Hill",
                "toAddress": "25 Palermo\\nCambelton Crescent",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 8210,
                "messengerId": "ffd4c856-644f-4453-a5ed-84689801a747",
                "distance": 1642
              },
              "basket": {
                "items": [
                  {
                    "name": "Chicken Sharebag",
                    "quantity": 1,
                    "price": 224.2,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": []
                  }
                ]
              },
              "customerId": "a101c61c-9908-4ff1-b55c-48599876ea90",
              "shopId": "79b48893-6713-43b1-8af8-d6be65c61f07",
              "description": "payfast-undefined",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 548.2230000000001,
              "messengerPaid": false,
              "smsSentToShop": false,
              "smsSentToAdmin": false,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_3_READY_FOR_COLLECTION",
              "shippingData": {
                "fromAddress": "Ekasi Grillz",
                "toAddress": "25 Palermo\\nCambelton Crescent",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 8180,
                "messengerId": "ffd4c856-644f-4453-a5ed-84689801a747",
                "distance": 1635
              },
              "basket": {
                "items": [
                  {
                    "name": "Wrap (Chicken) & Chips",
                    "quantity": 1,
                    "price": 3.8809286885721086,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": [
                      {
                        "name": "Chips Sauce 1",
                        "values": [
                          "Chili",
                          "Mayonnaise",
                          "Tomato",
                          "None"
                        ],
                        "selected": "Chili",
                        "price": 0
                      },
                      {
                        "name": "Chips Sauce 2",
                        "values": [
                          "Chili",
                          "Mayonnaise",
                          "Tomato",
                          "None"
                        ],
                        "selected": "Tomato",
                        "price": 0
                      },
                      {
                        "name": "300ml cool drink",
                        "values": [
                          "Coke",
                          "Sprite",
                          "Stoney",
                          "None"
                        ],
                        "selected": "Stoney",
                        "price": 10
                      }
                    ]
                  }
                ]
              },
              "customerId": "a101c61c-9908-4ff1-b55c-48599876ea90",
              "shopId": "a3873c5f-9884-4d02-b712-00a70019e0d1",
              "description": "payfast-1637737114",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 531.9522000000001,
              "messengerPaid": false,
              "smsSentToShop": false,
              "smsSentToAdmin": false,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_1_WAITING_STORE_CONFIRM",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "5 Sigwegwe Rd, Esibubulungu, Newlands East, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 15,
                "messengerId": "8058aaed-619b-4547-a727-dd68c84c01ac",
                "distance": 1
              },
              "basket": {
                "items": [
                  {
                    "name": "Streetwise Two Chips",
                    "quantity": 1,
                    "price": 38.9,
                    "discountPerc": 0,
                    "storePrice": 34.9,
                    "options": []
                  }
                ]
              },
              "customerId": "6c45f9e0-dd55-4059-870c-08d578a259e6",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "Payment from 0734396642: order 1637772284",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 3.5035,
              "messengerPaid": false,
              "smsSentToShop": true,
              "smsSentToAdmin": true,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_1_WAITING_STORE_CONFIRM",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "5 Sigwegwe Rd, Esibubulungu, Newlands East, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "SCHEDULED_DELIVERY",
                "fee": 15,
                "distance": 1
              },
              "basket": {
                "items": [
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 1,
                    "price": 49.9,
                    "discountPerc": 0,
                    "storePrice": 44.9,
                    "options": []
                  }
                ]
              },
              "customerId": "6c45f9e0-dd55-4059-870c-08d578a259e6",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "Payment from 0734396642: order 1637772365",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 4.218500000000001,
              "messengerPaid": false,
              "smsSentToShop": true,
              "smsSentToAdmin": true,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_1_WAITING_STORE_CONFIRM",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "57 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 15,
                "messengerId": "8058aaed-619b-4547-a727-dd68c84c01ac",
                "distance": 2
              },
              "basket": {
                "items": [
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 1,
                    "price": 49.9,
                    "discountPerc": 0,
                    "storePrice": 44.9,
                    "options": []
                  }
                ]
              },
              "customerId": "1ce1f02a-0819-40f5-bd3e-0372a0b649a7",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "payfast-1637775882",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 4.218500000000001,
              "messengerPaid": false,
              "smsSentToShop": true,
              "smsSentToAdmin": true,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_1_WAITING_STORE_CONFIRM",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "57 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 15,
                "messengerId": "8058aaed-619b-4547-a727-dd68c84c01ac",
                "distance": 2
              },
              "basket": {
                "items": [
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 1,
                    "price": 49.9,
                    "discountPerc": 0,
                    "storePrice": 44.9,
                    "options": []
                  },
                  {
                    "name": "Krusher",
                    "quantity": 1,
                    "price": 32.9,
                    "discountPerc": 0,
                    "storePrice": 29.9,
                    "options": [
                      {
                        "name": "Flavor",
                        "selected": "Oreo",
                        "price": 0
                      }
                    ]
                  }
                ]
              },
              "customerId": "1ce1f02a-0819-40f5-bd3e-0372a0b649a7",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "payfast-1637781397",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 6.357,
              "messengerPaid": false,
              "smsSentToShop": true,
              "smsSentToAdmin": true,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_3_READY_FOR_COLLECTION",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "57 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "DELIVERY",
                "fee": 15,
                "messengerId": "c4004b7e-43a8-4e20-a565-ab551a9ebf65",
                "distance": 2
              },
              "basket": {
                "items": [
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 1,
                    "price": 44.9,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": []
                  },
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 1,
                    "price": 44.9,
                    "storePrice": 0,
                    "discountPerc": 0,
                    "options": []
                  }
                ]
              },
              "customerId": "a101c61c-9908-4ff1-b55c-48599876ea90",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "payfast-1639039889",
              "paymentType": "PAYFAST",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 6.812,
              "messengerPaid": false,
              "smsSentToShop": false,
              "smsSentToAdmin": false,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_1_WAITING_STORE_CONFIRM",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "5 Sigwegwe Rd, Esibubulungu, Newlands East, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "SCHEDULED_DELIVERY",
                "fee": 15,
                "messengerId": "8058aaed-619b-4547-a727-dd68c84c01ac",
                "distance": 1
              },
              "basket": {
                "items": [
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 1,
                    "price": 49.9,
                    "discountPerc": 0,
                    "storePrice": 44.9,
                    "options": []
                  }
                ]
              },
              "customerId": "6c45f9e0-dd55-4059-870c-08d578a259e6",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "Payment from 0734396642: order 1639183771",
              "paymentType": "SPEED_POINT",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 4.218500000000001,
              "messengerPaid": false,
              "smsSentToShop": true,
              "smsSentToAdmin": true,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_1_WAITING_STORE_CONFIRM",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "57 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "SCHEDULED_DELIVERY",
                "fee": 15,
                "messengerId": "8058aaed-619b-4547-a727-dd68c84c01ac",
                "distance": 2
              },
              "basket": {
                "items": [
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 2,
                    "price": 49.9,
                    "discountPerc": 0,
                    "storePrice": 44.9,
                    "options": []
                  },
                  {
                    "name": "Streetwise Two Chips",
                    "quantity": 1,
                    "price": 38.9,
                    "discountPerc": 0,
                    "storePrice": 34.9,
                    "options": []
                  }
                ]
              },
              "customerId": "6c45f9e0-dd55-4059-870c-08d578a259e6",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "Payment from 0734396642: order 1639186633",
              "paymentType": "SPEED_POINT",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": false,
              "serviceFee": 9.990499999999999,
              "messengerPaid": false,
              "smsSentToShop": true,
              "smsSentToAdmin": true,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            },{
              "stage": "STAGE_7_ALL_PAID",
              "shippingData": {
                "fromAddress": "KFC KwaMashu",
                "toAddress": "5 Sigwegwe Rd, Esibubulungu, Newlands East, 4051, South Africa",
                "buildingType": "HOUSE",
                "type": "SCHEDULED_DELIVERY",
                "fee": 15,
                "messengerId": "8058aaed-619b-4547-a727-dd68c84c01ac",
                "distance": 1
              },
              "basket": {
                "items": [
                  {
                    "name": "Krusher",
                    "quantity": 1,
                    "price": 32.9,
                    "discountPerc": 0,
                    "storePrice": 29.9,
                    "options": [
                      {
                        "name": "Flavor",
                        "selected": "Oreo",
                        "price": 0
                      }
                    ]
                  },
                  {
                    "name": "Sweet Chilli Twister",
                    "quantity": 1,
                    "price": 49.9,
                    "discountPerc": 0,
                    "storePrice": 44.9,
                    "options": []
                  }
                ]
              },
              "customerId": "6c45f9e0-dd55-4059-870c-08d578a259e6",
              "shopId": "cfe1b5fd-fcb5-43c5-92d7-807f7241c8e1",
              "description": "Payment from 0734396642: order 1639259007",
              "paymentType": "SPEED_POINT",
              "orderType": "ONLINE",
              "hasVat": false,
              "shopPaid": true,
              "serviceFee": 6.357,
              "messengerPaid": true,
              "smsSentToShop": false,
              "smsSentToAdmin": false,
              "freeDelivery": false,
              "minimumDepositAllowedPerc": 1
            }]
            """;
}