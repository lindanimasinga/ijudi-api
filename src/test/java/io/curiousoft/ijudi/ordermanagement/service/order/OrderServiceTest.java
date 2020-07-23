package io.curiousoft.ijudi.ordermanagement.service.order;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.notification.PushNotificationService;
import io.curiousoft.ijudi.ordermanagement.repo.DeviceRepository;
import io.curiousoft.ijudi.ordermanagement.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import io.curiousoft.ijudi.ordermanagement.service.OrderServiceImpl;
import io.curiousoft.ijudi.ordermanagement.service.PaymentService;
import io.curiousoft.ijudi.ordermanagement.service.SmsNotificationService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OrderServiceTest {

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
    private SmsNotificationService smsNotifcation;
    @Mock
    private DeviceRepository deviceRepo;

    //system under test
    private OrderServiceImpl sut;

    @Before
    public void setUp() throws Exception {
        double deliveryFee = 10;
        double serviceFee = 5;
        long cleanInMinutes = 5;
        sut = new OrderServiceImpl(
                deliveryFee,
                serviceFee,
                cleanInMinutes,
                repo,
                storeRepo,
                customerRepo,
                paymentService,
                deviceRepo,
                pushNotificationService,
                smsNotifcation);
    }

    @Test
    public void startOrderOnlineDelivery() throws Exception {

        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        storeProfile.setHasVat(false);

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);

        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));
        when(repo.save(order)).thenReturn(order);

        Order newOrder = sut.startOrder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_0_CUSTOMER_NOT_PAID, newOrder.getStage());
        Assert.assertNotNull(order.getId());
        Assert.assertNotNull(order.getDate());
        Assert.assertEquals(10, order.getShippingData().getFee(), 0);
        Assert.assertEquals(5.00, order.getServiceFee(), 0);
        Assert.assertEquals(40.00, order.getBasketAmount(), 0);
        Assert.assertEquals(false, order.getHasVat());
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).findById(order.getShopId());
    }

    @Test
    public void startOrderOnlineCollectionOrPayInstore() throws Exception {

        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        storeProfile.setHasVat(false);

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);

        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));
        when(repo.save(order)).thenReturn(order);

        Order newOrder = sut.startOrder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_0_CUSTOMER_NOT_PAID, newOrder.getStage());
        Assert.assertNotNull(order.getId());
        Assert.assertNotNull(order.getDate());
        Assert.assertEquals(5.00, order.getServiceFee(), 0);
        Assert.assertEquals(0, order.getShippingData().getFee(), 0);
        Assert.assertEquals(40.00, order.getBasketAmount(), 0);
        //verify total amount paid
        Assert.assertEquals(order.getServiceFee() + basket.getItems().stream()
                .mapToDouble(BasketItem::getPrice).sum() + shipping.getFee(), order.getTotalAmount(), 0);
        Assert.assertFalse(order.getHasVat());
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).findById(order.getShopId());
    }

    @Test
    public void startOrderStoreWithVAT() throws Exception {

        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        storeProfile.setHasVat(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        storeProfile.setFeaturedExpiry(date);

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);

        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));
        when(repo.save(order)).thenReturn(order);

        Order newOrder = sut.startOrder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_0_CUSTOMER_NOT_PAID, newOrder.getStage());
        Assert.assertNotNull(order.getId());
        Assert.assertNotNull(order.getDate());
        Assert.assertTrue(order.getHasVat());
        Assert.assertEquals(5.00, order.getServiceFee(), 0);
        Assert.assertEquals(10.00, order.getShippingData().getFee(), 0);
        Assert.assertEquals(40, order.getBasketAmount(), 0);
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).findById(order.getShopId());
    }

    @Test
    public void startOrderNoOrderType() throws Exception {

        //given
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);

        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setDescription("description");

        try {
            Order newOrder = sut.startOrder(order);
            fail();
        } catch (Exception e) {
            Assert.assertEquals("order type is not valid", e.getMessage());
        }
    }

    @Test
    public void startOrderCustomerNotExist() throws Exception {

        //given
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setOrderType(OrderType.ONLINE);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setDescription("description");

        //when
        try {
            Order newOrder = sut.startOrder(order);
            fail();
        } catch (Exception e) {
            //verify
            verifyNoInteractions(repo);
            verify(customerRepo).existsById(order.getCustomerId());
        }
    }

    @Test
    public void startOrderShopNotExist() throws Exception {

        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        storeProfile.setHasVat(false);

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);

        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        //when
        try {
            Order newOrder = sut.startOrder(order);
            fail();
        } catch (Exception e) {
            //verify
            verifyNoInteractions(repo);
        }
    }

    @Test
    public void startOrderNoShippingData() {

        try {
            //given
            Order order = new Order();
            Basket basket = new Basket();
            List<BasketItem> items = new ArrayList<>();
            items.add(new BasketItem("chips", 2, 10, 0));
            items.add(new BasketItem("hotdog", 1, 20, 0));
            basket.setItems(items);
            order.setBasket(basket);

            UserProfile messenger = new UserProfile(
                    "99091111222323",
                    "testName",
                    "41 Sheffs, Afr, 8009",
                    "Https://url.com",
                    ProfileRoles.MESSENGER);

            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
            order.setOrderType(OrderType.ONLINE);
            order.setDescription("description");

            //when

            Order newOrder = sut.startOrder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Order shipping is null or pickup time or messenger not valid or shipping address not valid", e.getMessage());
        }
    }

    @Test
    public void finishOrderNotExist() throws Exception {

        //given
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setOrderType(OrderType.ONLINE);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setDescription("description");

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.empty());
        try {
            Order newOrder = sut.finishOder(order);
            fail();
        } catch (Exception e) {
            //verify
            verify(repo).findById(order.getId());
        }
    }

    @Test
    public void finishOrderNoShippingData() {

        try {
            //given
            Order order = new Order();
            Basket basket = new Basket();
            List<BasketItem> items = new ArrayList<>();
            items.add(new BasketItem("chips", 2, 10, 0));
            items.add(new BasketItem("hotdog", 1, 20, 0));
            basket.setItems(items);
            order.setBasket(basket);

            UserProfile messenger = new UserProfile(
                    "99091111222323",
                    "testName",
                    "41 Sheffs, Afr, 8009",
                    "Https://url.com",
                    ProfileRoles.MESSENGER);

            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setOrderType(OrderType.ONLINE);
            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
            order.setDescription("desc");

            //when

            Order newOrder = sut.finishOder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Order shipping is null or pickup time or messenger not valid or shipping address not valid", e.getMessage());
        }
    }

    @Test
    public void finishOrderNoCollectionPickupTime() {

        try {
            //given
            Order order = new Order();
            Basket basket = new Basket();
            List<BasketItem> items = new ArrayList<>();
            items.add(new BasketItem("chips", 2, 10, 0));
            items.add(new BasketItem("hotdog", 1, 20, 0));
            basket.setItems(items);
            order.setBasket(basket);

            UserProfile messenger = new UserProfile(
                    "99091111222323",
                    "testName",
                    "41 Sheffs, Afr, 8009",
                    "Https://url.com",
                    ProfileRoles.MESSENGER);

            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setOrderType(OrderType.ONLINE);
            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
            ShippingData shipping = new ShippingData();
            shipping.setFromAddress("");
            shipping.setToAddress("");
            shipping.setType(ShippingData.ShippingType.DELIVERY);
            order.setShippingData(shipping);
            order.setDescription("desc");

            //when

            Order newOrder = sut.finishOder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            boolean isvald = e.getMessage().equals("Order shipping is null or pickup time or messenger not valid or shipping address not valid") ||
                    e.getMessage().equals("shipping address not valid");
            Assert.assertTrue(isvald);
        }
    }

    @Test
    public void startOrderNoCustomer() {

        try {

            //given
            Order order = new Order();
            Basket basket = new Basket();
            List<BasketItem> items = new ArrayList<>();
            items.add(new BasketItem("chips", 2, 10, 0));
            items.add(new BasketItem("hotdog", 1, 20, 0));
            basket.setItems(items);
            order.setBasket(basket);

            Messager messenger = new Messager();
            messenger.setId("messagerID");

            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY);
            shipping.setMessenger(messenger);
            order.setShippingData(shipping);
            order.setOrderType(OrderType.ONLINE);
            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
            order.setShopId("shopid");
            order.setDescription("desc");

            //when

            Order newOrder = sut.startOrder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order customer is not valid", e.getMessage());
        }
    }

    @Test
    public void startOrderNoShop() {

        try {

            //given
            Order order = new Order();
            Basket basket = new Basket();
            List<BasketItem> items = new ArrayList<>();
            items.add(new BasketItem("chips", 2, 10, 0));
            items.add(new BasketItem("hotdog", 1, 20, 0));
            basket.setItems(items);
            order.setBasket(basket);

            Messager messenger = new Messager();
            messenger.setId("messagerID");

            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY);
            shipping.setMessenger(messenger);
            order.setShippingData(shipping);

            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
            order.setOrderType(OrderType.ONLINE);
            order.setCustomerId("1234");
            order.setDescription("description");

            //when

            Order newOrder = sut.startOrder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order shop is not valid", e.getMessage());
        }
    }

    @Test
    public void startOrderNoBasket() {

        try {

            //given
            Order order = new Order();

            Messager messenger = new Messager();
            messenger.setId("messagerID");

            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY);
            shipping.setMessenger(messenger);
            order.setShippingData(shipping);

            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
            order.setOrderType(OrderType.ONLINE);
            order.setDescription("desc");

            //when

            Order newOrder = sut.startOrder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order basket is not valid", e.getMessage());
        }
    }

    @Test
    public void finishOderOnline() throws Exception {

        //given
        List<Device> storeDevices = Collections.singletonList(new Device("token"));
        List<Device> messengerDevices = Collections.singletonList(new Device("token"));
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setShopId(shopId);
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        shop.setBusinessHours(new ArrayList<>());
        shop.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0);
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        shop.setStockList(stockList);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.paymentReceived(order)).thenReturn(true);
        when(repo.save(order)).thenReturn(order);
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(shop));
        when(deviceRepo.findByUserId(shop.getOwnerId())).thenReturn(storeDevices);
        when(deviceRepo.findByUserId(order.getShippingData().getMessenger().getId())).thenReturn(messengerDevices);

        Order finalOrder = sut.finishOder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM, finalOrder.getStage());
        Assert.assertNotNull(finalOrder.getDescription());
        Assert.assertTrue(finalOrder.getDate().after(orderDate));
        Assert.assertFalse(finalOrder.getShopPaid());
        Assert.assertFalse(order.getHasVat());
        verify(repo).save(order);
        verify(paymentService).paymentReceived(order);
        verify(repo).findById(order.getId());
        verify(storeRepo).findById(shopId);
        verify(storeRepo).save(shop);
        verify(pushNotificationService).notifyStoreOrderPlaced(storeDevices, order);
        verify(pushNotificationService).notifyMessengerOrderPlaced(messengerDevices, order, shop);
        verify(deviceRepo).findByUserId(shop.getOwnerId());
    }

    @Test
    public void finishOderInStore() throws Exception {

        //given
        List<Device> storeDevices = Collections.singletonList(new Device("token"));
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.INSTORE);
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId(shopId);
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        shop.setBusinessHours(new ArrayList<>());
        shop.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0);
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        shop.setStockList(stockList);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.paymentReceived(order)).thenReturn(true);
        when(paymentService.paymentReceived(order)).thenReturn(true);
        when(paymentService.completePaymentToShop(order)).thenReturn(true);
        when(repo.save(order)).thenReturn(order);
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(shop));
        when(deviceRepo.findByUserId(shop.getOwnerId())).thenReturn(storeDevices);

        Order finalOrder = sut.finishOder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_7_PAID_SHOP, finalOrder.getStage());
        Assert.assertEquals(0, finalOrder.getShippingData().getFee(), 0);
        Assert.assertEquals(5, finalOrder.getServiceFee(), 0);
        Assert.assertTrue(finalOrder.getShopPaid());
        Assert.assertTrue(finalOrder.getDate().after(orderDate));
        Assert.assertNotNull(finalOrder.getDescription());
        Assert.assertTrue(order.getHasVat() == false);
        verify(repo).save(order);
        verify(paymentService).paymentReceived(order);
        verify(paymentService).completePaymentToShop(order);
        verify(repo).findById(order.getId());
        verify(storeRepo).findById(shopId);
        verify(storeRepo).save(shop);
        verify(pushNotificationService).notifyStoreOrderPlaced(storeDevices, order);
        verify(deviceRepo).findByUserId(shop.getOwnerId());

    }

    @Test
    public void finishOderInStoreAlreadyPaid() throws Exception {

        //given
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.INSTORE);
        order.setStage(OrderStage.STAGE_7_PAID_SHOP);
        order.setShopPaid(true);
        order.setShopId(shopId);
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        shop.setBusinessHours(new ArrayList<>());
        shop.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0);
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        shop.setStockList(stockList);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.paymentReceived(order)).thenReturn(true);

        Order finalOrder = sut.finishOder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_7_PAID_SHOP, finalOrder.getStage());
        Assert.assertTrue(finalOrder.getShopPaid());
        Assert.assertTrue(finalOrder.getDate().after(orderDate));
        Assert.assertNotNull(finalOrder.getDescription());
        Assert.assertTrue(order.getHasVat() == false);
        verify(repo, never()).save(order);
        verify(paymentService).paymentReceived(order);
        verify(paymentService, never()).completePaymentToShop(order);
        verify(repo).findById(order.getId());
        verify(storeRepo, never()).findById(shopId);
        verify(storeRepo, never()).save(shop);

    }

    @Test
    public void finishOrderNoBasket() {

        try {
            //given
            Order order = new Order();

            Messager messenger = new Messager();
            messenger.setId("messagerID");

            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY);
            shipping.setMessenger(messenger);
            order.setShippingData(shipping);
            order.setOrderType(OrderType.ONLINE);
            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
            order.setDescription("desc");

            //when

            Order newOrder = sut.finishOder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order basket is not valid", e.getMessage());
        }
    }

    @Test
    public void findOrderByUserId() {

        //given
        String customerId = "id of customer";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setCustomerId(customerId);
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setShopId("shopid");

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger2);
        order2.setShippingData(shipping2);
        order2.setCustomerId(customerId);
        order2.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order2.setShopId("shopid");

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        //when
        when(repo.findByCustomerId(customerId)).thenReturn(Optional.of(orders));
        List<Order> finalOrder = sut.findOrderByUserId(customerId);

        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(2, finalOrder.size());
        Assert.assertEquals(customerId, finalOrder.get(0).getCustomerId());
        verify(repo).findByCustomerId(customerId);

    }

    @Test
    public void findOrderByUserIdNoOrders() {

        //given
        String customerId = "id of customer";

        //when
        List<Order> finalOrder = sut.findOrderByUserId(customerId);

        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(0, finalOrder.size());
        verify(repo).findByCustomerId(customerId);

    }

    @Test
    public void findOrderByPhone() throws Exception {
        //given
        String customerId = "id of customer";
        String phoneNumber = "0812445563";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setCustomerId(customerId);
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId("shopid");

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger2);
        order2.setShippingData(shipping2);
        order2.setCustomerId(customerId);
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order2.setShopId("shopid");

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        UserProfile initialProfile = new UserProfile(
                "name",
                "address",
                "https://image.url",
                phoneNumber,
                ProfileRoles.CUSTOMER);
        initialProfile.setId("initialID");

        //when
        when(customerRepo.findByMobileNumber(phoneNumber)).thenReturn(Optional.of(initialProfile));
        when(repo.findByCustomerId(initialProfile.getId())).thenReturn(Optional.of(orders));
        List<Order> finalOrder = sut.findOrderByPhone(phoneNumber);

        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(2, finalOrder.size());
        Assert.assertEquals(customerId, finalOrder.get(0).getCustomerId());
        verify(repo).findByCustomerId(initialProfile.getId());
        verify(customerRepo).findByMobileNumber(phoneNumber);
    }

    @Test
    public void finishOderNoStock() throws Exception {

        //given
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        BasketItem item = new BasketItem("chips", 10, 1, 0);
        basket.setItems(Collections.singletonList(item));
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setShopId(shopId);
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        shop.setBusinessHours(new ArrayList<>());
        shop.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0);
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        shop.setStockList(stockList);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.paymentReceived(order)).thenReturn(true);
        when(repo.save(order)).thenReturn(order);
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(shop));

        Order finalOrder = sut.finishOder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM, finalOrder.getStage());
        Assert.assertNotNull(finalOrder.getDescription());
        verify(repo).save(order);
        verify(paymentService).paymentReceived(order);
        verify(repo).findById(order.getId());
        verify(storeRepo).findById(shopId);
        verify(storeRepo).save(shop);

    }

    @Test
    public void progressNextStageOnlineDelivery() throws Exception {
        //given
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setShopId(shopId);
        order.setDescription("desc");

        Device device = new Device("token");
        PushHeading title = new PushHeading("The store has started processing your order " + order.getId(),
                "Order Status Updated", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(repo.save(order)).thenReturn(order);
        when(deviceRepo.findByUserId(order.getCustomerId())).thenReturn(Collections.singletonList(device));

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_2_STORE_PROCESSING, finalOrder.getStage());
        verify(deviceRepo).findByUserId(order.getCustomerId());
        verify(pushNotificationService).sendNotification(device, message);
        verify(repo).findById(order.getId());
        verify(repo).save(order);
    }

    @Test
    public void progressNextStageOnlineDeliveryReadyForCollection() throws Exception {
        //given
        String shopId = "shopid";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId(shopId);
        order.setDescription("desc");

        Device device = new Device("token");
        PushHeading title = new PushHeading("Food is ready for Collection at " + shop.getName(),
                "Order Status Updated", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(repo.save(order)).thenReturn(order);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(shop));
        when(deviceRepo.findByUserId(order.getShippingData().getMessenger().getId())).thenReturn(Collections.singletonList(device));

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_3_READY_FOR_COLLECTION, finalOrder.getStage());
        verify(deviceRepo).findByUserId(order.getShippingData().getMessenger().getId());
        verify(pushNotificationService).sendNotification(device, message);
        verify(storeRepo).findById(order.getShopId());
        verify(repo).findById(order.getId());
        verify(repo).save(order);
    }

    @Test
    public void progressNextStageOnlineDeliveryDriverOnHisWay() throws Exception {
        //given
        String shopId = "shopid";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        order.setShopId(shopId);
        order.setDescription("desc");

        Device device = new Device("token");
        PushHeading title = new PushHeading("The driver is on the way",
                "Order Status Updated", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(repo.save(order)).thenReturn(order);
        when(deviceRepo.findByUserId(order.getCustomerId())).thenReturn(Collections.singletonList(device));

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_4_ON_THE_ROAD, finalOrder.getStage());
        verify(paymentService).completePaymentToShop(order);
        verify(deviceRepo).findByUserId(order.getCustomerId());
        verify(pushNotificationService).sendNotification(device, message);
        verify(repo).findById(order.getId());
        verify(repo).save(order);
    }

    @Test
    public void progressNextStageOnlineDeliveryDriverArrived() throws Exception {
        //given
        String shopId = "shopid";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_4_ON_THE_ROAD);
        order.setShopId(shopId);
        order.setDescription("desc");

        Device device = new Device("token");
        PushHeading title = new PushHeading("The driver has arrived",
                "Order Status Updated", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(repo.save(order)).thenReturn(order);
        when(deviceRepo.findByUserId(order.getCustomerId())).thenReturn(Collections.singletonList(device));

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_5_ARRIVED, finalOrder.getStage());
        verify(deviceRepo).findByUserId(order.getCustomerId());
        verify(pushNotificationService).sendNotification(device, message);
        verify(repo).findById(order.getId());
        verify(repo).save(order);
    }

    @Test
    public void progressNextStageInstore() throws Exception {
        //given
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.INSTORE);
        order.setStage(OrderStage.STAGE_7_PAID_SHOP);
        order.setShopId(shopId);
        order.setDescription("desc");

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_7_PAID_SHOP, finalOrder.getStage());
        verify(repo).findById(order.getId());
    }

    @Test
    public void progressLastStageOnlineDelivery() throws Exception {
        //given
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_7_PAID_SHOP);
        order.setShopId(shopId);
        order.setDescription("desc");

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_7_PAID_SHOP, finalOrder.getStage());
        verify(repo).findById(order.getId());
    }

    @Test
    public void progressStage6OnlineDelivery() throws Exception {
        //given
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId(shopId);
        order.setDescription("desc");

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(repo.save(order)).thenReturn(order);
        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_6_WITH_CUSTOMER, finalOrder.getStage());
        Assert.assertNotEquals(orderDate, finalOrder.getDate());
        verify(repo).findById(order.getId());
    }

    @Test
    public void progressNextStageOnlineCollectionReady() throws Exception {
        //given
        String shopId = "shopid";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        storeProfile.setHasVat(false);

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId(shopId);
        order.setDescription("desc");

        Device device = new Device("token");
        PushHeading title = new PushHeading("Food is ready for Collection at " + storeProfile.getName(),
                "Order Status Updated", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));
        when(repo.save(order)).thenReturn(order);
        when(deviceRepo.findByUserId(order.getCustomerId())).thenReturn(Collections.singletonList(device));


        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_3_READY_FOR_COLLECTION, finalOrder.getStage());
        verify(deviceRepo).findByUserId(order.getCustomerId());
        verify(pushNotificationService).sendNotification(device, message);
        verify(repo).findById(order.getId());
        verify(repo).save(order);
    }

    @Test
    public void progressNextStageOnlineCollection() throws Exception {
        //given
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        order.setShopId(shopId);
        order.setDescription("desc");

        Device device = new Device("token");
        PushHeading title = new PushHeading("The store has started processing your order " + order.getId(),
                "Order Status Updated", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(repo.save(order)).thenReturn(order);


        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_6_WITH_CUSTOMER, finalOrder.getStage());
        verify(paymentService).completePaymentToShop(order);
        verify(repo).findById(order.getId());
        verify(repo).save(order);
    }

    @Test
    public void findOrderByStoreId() throws Exception {
        //given
        String shopId = "id of shop";
        String phoneNumber = "0812445563";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId(shopId);

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order2.setShopId(shopId);

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);

        //when
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(initialProfile));
        when(repo.findByShopIdAndStageNot(initialProfile.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID)).thenReturn(orders);

        List<Order> finalOrder = sut.findOrderByStoreId(shopId);

        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(2, finalOrder.size());
        Assert.assertEquals(shopId,  finalOrder.get(0).getShopId());
        verify(storeRepo).findById(shopId);
        verify(repo).findByShopIdAndStageNot(initialProfile.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }

    @Test
    public void findOrderByMessengerId() throws Exception {
        //given
        String shopId = "id of shop";

        UserProfile patchProfileRequest = new UserProfile(
                "secondName",
                "address2",
                "https://image.url2",
                "078mobilenumb",
                ProfileRoles.MESSENGER);

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId(patchProfileRequest.getId());
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId(shopId);

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order2.setShopId(shopId);

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        //when
        when(repo.findByShippingDataMessengerIdAndStageNot(patchProfileRequest.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID)).thenReturn(orders);

        List<Order> finalOrder = sut.findOrderByMessengerId(patchProfileRequest.getId());

        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(2, finalOrder.size());
        Assert.assertEquals(patchProfileRequest.getId(),  finalOrder.get(0).getShippingData().getMessenger().getId());
        verify(repo).findByShippingDataMessengerIdAndStageNot(patchProfileRequest.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }

    @Test
    public void findOrderByStoreIdNoUpdaidOrdersRetuned() throws Exception {
        //given
        String shopId = "id of shop";
        String phoneNumber = "0812445563";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId(shopId);

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order2.setShopId(shopId);

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);

        //when
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(initialProfile));
        when(repo.findByShopIdAndStageNot(initialProfile.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID)).thenReturn(orders);

        List<Order> finalOrder = sut.findOrderByStoreId(shopId);

        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(1, finalOrder.size());
        finalOrder.forEach(data -> Assert.assertNotSame(data.getStage(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID));
        Assert.assertEquals(shopId, finalOrder.get(0).getShopId());
        verify(repo).findByShopIdAndStageNot(initialProfile.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        verify(storeRepo).findById(shopId);
    }

    @Test
    public void cleanUnPaidOrders() {
        //given
        String shopId = "id of shop";
        String phoneNumber = "0812445563";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId(shopId);

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order2.setShopId(shopId);

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);

        //when
        sut.cleanUnpaidOrders();

        //verify
        verify(repo).deleteByShopPaidAndStageAndDateBefore(eq(false), eq(OrderStage.STAGE_0_CUSTOMER_NOT_PAID),
                any(Date.class));
    }

    @Test
    public void checkNotAcceptedOrdersAndNotify() throws Exception {
        //given
        String shopId = "id of shop";
        String phoneNumber = "0812445563";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(10).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setShopId(shopId);

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        Date pickUpTime = Date.from(LocalDateTime.now().minusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping2.setPickUpTime(pickUpTime);
        shipping.setMessenger(messenger2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant());
        order2.setDate(orderDate);
        order2.setShopId(shopId);

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);

        //when
        when(repo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM)).thenReturn(orders);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(initialProfile));

        sut.checkUnconfirmedOrders();

        //verify
        verify(repo).findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        verify(smsNotifcation, times(1))
                .sendMessage(initialProfile.getMobileNumber(),
                        "Hello " + initialProfile.getName() + ", Please accept the order " + order.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
    }

    @Test
    public void checkNotAcceptedOrdersAndNotNotify() throws Exception {
        //given
        String shopId = "id of shop";
        String phoneNumber = "0812445563";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(9).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setShopId(shopId);

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        Date pickUpTime = Date.from(LocalDateTime.now().minusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping2.setPickUpTime(pickUpTime);
        shipping.setMessenger(messenger2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant());
        order2.setDate(orderDate);
        order2.setShopId(shopId);

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
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
        String shopId = "id of shop";
        String phoneNumber = "0812445563";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessenger(messenger);
        Date pickUpTime = Date.from(LocalDateTime.now().plusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(pickUpTime);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(66).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setShopId(shopId);

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessenger(messenger2);
        Date pickUpTime2 = Date.from(LocalDateTime.now()
                .plusMinutes(60)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        shipping2.setPickUpTime(pickUpTime2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(60).atZone(ZoneId.systemDefault()).toInstant());
        order2.setDate(orderDate2);
        order2.setShopId(shopId);

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);

        //when
        when(repo.findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM)).thenReturn(orders);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(initialProfile));

        sut.checkUnconfirmedOrders();

        //verify
        verify(repo).findByStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        verify(smsNotifcation, times(1))
                .sendMessage(initialProfile.getMobileNumber(),
                        "Hello " + initialProfile.getName() + ", Please accept the order " + order2.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
    }

    @Test
    public void checkNotAcceptedCollectionOrdersAndNotNotify() throws Exception {
        //given
        String shopId = "id of shop";
        String phoneNumber = "0812445563";

        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessenger(messenger);
        Date pickUpTime = Date.from(LocalDateTime.now().plusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(pickUpTime);
        order.setShippingData(shipping);
        order.setCustomerId("customer");
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate = Date.from(LocalDateTime.now().minusMinutes(66).atZone(ZoneId.systemDefault()).toInstant());
        order.setDate(orderDate);
        order.setShopId(shopId);

        //order 2
        Order order2 = new Order();
        Basket basket2 = new Basket();
        order.setBasket(basket);
        Messager messenger2 = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessenger(messenger2);
        Date pickUpTime2 = Date.from(LocalDateTime.now()
                .plusMinutes(61)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        shipping2.setPickUpTime(pickUpTime2);
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(60).atZone(ZoneId.systemDefault()).toInstant());
        order2.setDate(orderDate2);
        order2.setShopId(shopId);

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
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
                        "Hello " + initialProfile.getName() + ", Please accept the order " + order2.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
    }
}