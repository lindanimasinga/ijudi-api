package io.curiousoft.ijudi.ordermanagement.service.order;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.notification.PushNotificationService;
import io.curiousoft.ijudi.ordermanagement.repo.DeviceRepository;
import io.curiousoft.ijudi.ordermanagement.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import io.curiousoft.ijudi.ordermanagement.service.OrderServiceImpl;
import io.curiousoft.ijudi.ordermanagement.service.PaymentService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
    private DeviceRepository deviceRepo;

    //system under test
    private OrderServiceImpl sut;

    @Before
    public void setUp() throws Exception {
        sut = new OrderServiceImpl(
                repo,
                storeRepo,
                customerRepo,
                paymentService,
                deviceRepo,
                pushNotificationService);
    }

    @Test
    public void startOrder() throws Exception {

        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                ProfileRoles.CUSTOMER,
                businessHours,
                "ownerId");
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
        Assert.assertEquals(false, order.getHasVat());
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
                ProfileRoles.CUSTOMER,
                businessHours,
                "ownerId");
        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        storeProfile.setHasVat(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        storeProfile.setFeaturedExpiry(date);

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
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).findById(order.getShopId());
    }

    @Test
    public void startOrderNoOrderType() throws Exception {

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
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ProfileRoles.CUSTOMER,
                businessHours,
                "ownerId");
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
        order.setBasket(basket);

        Messager messenger = new Messager();
        messenger.setId("messagerID");

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY,
                10);
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
            order.setBasket(basket);

            Messager messenger = new Messager();
            messenger.setId("messagerID");

            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY,
                    10);
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
            order.setBasket(basket);

            Messager messenger = new Messager();
            messenger.setId("messagerID");

            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY,
                    10);
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
                    ShippingData.ShippingType.DELIVERY,
                    10);
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
        String shopId = "shopid";
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
                ProfileRoles.CUSTOMER,
                businessHours,
                "ownerId");
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
        verify(pushNotificationService).notifyOrderPlaced(storeDevices, order);
        verify(deviceRepo).findByUserId(shop.getOwnerId());
    }

    @Test
    public void finishOderInStore() throws Exception {

        //given
        List<Device> storeDevices = Collections.singletonList(new Device("token"));
        String shopId = "shopid";
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
                ProfileRoles.CUSTOMER,
                businessHours,
                "ownerId");
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
        verify(pushNotificationService).notifyOrderPlaced(storeDevices, order);
        verify(deviceRepo).findByUserId(shop.getOwnerId());

    }

    @Test
    public void finishOderInStoreAlreadyPaid() throws Exception {

        //given
        String shopId = "shopid";
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
                ProfileRoles.CUSTOMER,
                businessHours,
                "ownerId");
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
                    ShippingData.ShippingType.DELIVERY,
                    10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ProfileRoles.CUSTOMER,
                businessHours,
                "ownerId");
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
                ShippingData.ShippingType.DELIVERY,
                10);
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

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_2_STORE_PROCESSING, finalOrder.getStage());
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_6_WITH_CUSTOMER, finalOrder.getStage());
        verify(repo).findById(order.getId());
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
                ShippingData.ShippingType.COLLECTION,
                10);
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

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));

        Order finalOrder = sut.progressNextStage(order.getId());

        //verify
        Assert.assertEquals(OrderStage.STAGE_6_WITH_CUSTOMER, finalOrder.getStage());
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ProfileRoles.CUSTOMER,
                businessHours,
                "ownerId");
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);

        //when
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(initialProfile));
        when(repo.findByShopId(initialProfile.getId())).thenReturn(orders);
        when(repo.findByShopIdAndStageNot(initialProfile.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID)).thenReturn(orders);

        List<Order> finalOrder = sut.findOrderByStoreId(shopId);

        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(2, finalOrder.size());
        Assert.assertEquals(shopId, finalOrder.get(0).getShopId());
        verify(storeRepo).findById(shopId);
        verify(repo).findByShopIdAndStageNot(initialProfile.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ShippingData.ShippingType.DELIVERY,
                10);
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
                ProfileRoles.STORE,
                businessHours,
                "ownerId");
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
}