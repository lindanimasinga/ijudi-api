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
import org.junit.Ignore;
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

@Ignore
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
    List<String> phoneNumbers = Collections.singletonList("08128155660");

    //system under test
    private OrderServiceImpl sut;


    @Before
    public void setUp() {
        double standardDeliveryFee = 10;
        double standardDeliveryKm = 10;
        double ratePerKm = 1;
        double serviceFee = 0.025;
        long cleanInMinutes = 5;
        sut = new OrderServiceImpl(
                standardDeliveryFee,
                standardDeliveryKm,
                ratePerKm,
                serviceFee,
                cleanInMinutes,
                phoneNumbers,
                "AIzaSyAZbvE4NBcJIplfzmy8cSEdSpbocBggylc",
                repo,
                storeRepo,
                customerRepo,
                paymentService,
                deviceRepo,
                pushNotificationService,
                smsNotifcation);
    }

    private StoreProfile createStoreProfile(StoreType food) {
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = new StoreProfile(
                food,
                "name",
                "shortname",
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
        storeProfile.setLatitude(-29.7287185);
        storeProfile.setLongitude(30.9344252);
        storeProfile.setId("shopid");
        return storeProfile;
    }

    @Test
    public void startOrderOnlineDelivery() throws Exception {
        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Set<Stock> stockItems = new HashSet<>();
        stockItems.add(new Stock("chips", 2, 10, 0, Collections.emptyList()));
        stockItems.add(new Stock("hotdog", 1, 20, 0, Collections.emptyList()));
        storeProfile.setStockList(stockItems);
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData("shopAddress",
                "46 Ududu Rd, Emlandweni, KwaMashu, 4051",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
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
        Assert.assertEquals(10, order.getShippingData().getFee(), 0);
        Assert.assertEquals(1.25, order.getServiceFee(), 0);
        Assert.assertEquals(40.00, order.getBasketAmount(), 0);
        Assert.assertEquals(false, order.getHasVat());
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).findById(order.getShopId());
    }



    @Test
    public void startOrder_store_own_delivery() throws Exception {
        //given
        StoreProfile storeProfile = createStoreProfile(StoreType.CLOTHING);

        Messager messager = new Messager("Postnet", 20.00, 3, 5);
        storeProfile.setStoreMessenger(messager);

        Set<Stock> stockItems = new HashSet<>();
        stockItems.add(new Stock("skirt", 2, 10, 0, Collections.emptyList()));
        stockItems.add(new Stock("shirt", 1, 20, 0, Collections.emptyList()));
        storeProfile.setStockList(stockItems);

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("skirt", 2, 10, 0));
        items.add(new BasketItem("shirt", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData("shopAddress",
                "50 Ududu Rd, Emlandweni, KwaMashu, 4051",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
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

        //test
        Order newOrder = sut.startOrder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_0_CUSTOMER_NOT_PAID, newOrder.getStage());
        Assert.assertNotNull(order.getId());
        Assert.assertEquals(45.00, order.getShippingData().getFee(), 0);
        Assert.assertEquals(0, order.getServiceFee(), 0);
        Assert.assertEquals(40.00, order.getBasketAmount(), 0);
        Assert.assertEquals(false, order.getHasVat());
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).findById(order.getShopId());
    }

    @Test
    public void startOrderOnlineDeliveryNoBuildingType() throws Exception {
        //given
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");
        try {
            Order newOrder = sut.startOrder(order);
            fail();
        }catch (Exception e) {
            Assert.assertEquals("Order shipping is null or pickup time or messenger not valid or shipping address not valid", e.getMessage());
        }
    }
    @Test
    public void startOrderOnlineDeliveryNoBuildingUnit() throws Exception {
        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.APARTMENT);
        shipping.setBuildingName("nameofbuilding");
        order.setShippingData(shipping);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");
        try {
            Order newOrder = sut.startOrder(order);
            fail();
        }catch (Exception e) {
            Assert.assertEquals("Order shipping is null or pickup time or messenger not valid or shipping address not valid", e.getMessage());
        }
    }
    @Test
    public void startOrderOnlineDeliveryNoBuildingName() throws Exception {
        //given
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.APARTMENT);
        shipping.setUnitNumber("unit number for building");
        order.setShippingData(shipping);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");
        try {
            Order newOrder = sut.startOrder(order);
            fail();
        }catch (Exception e) {
            Assert.assertEquals("Order shipping is null or pickup time or messenger not valid or shipping address not valid", e.getMessage());
        }
    }
    @Test
    public void startOrderPayInstore() throws Exception {
        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.INSTORE);
        order.setDescription("description");
        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));
        when(repo.save(order)).thenReturn(order);
        Order newOrder = sut.startOrder(order);
        //verify
        Assert.assertEquals(OrderStage.STAGE_0_CUSTOMER_NOT_PAID, newOrder.getStage());
        Assert.assertNotNull(order.getId());
        Assert.assertEquals(1.00, order.getServiceFee(), 0);
        Assert.assertNull(order.getShippingData());
        Assert.assertEquals(40.00, order.getBasketAmount(), 0);
        //verify total amount paid
        Assert.assertEquals(order.getServiceFee() + basket.getItems().stream()
                .mapToDouble(BasketItem::getTotalPrice).sum(), order.getTotalAmount(), 0);
        Assert.assertFalse(order.getHasVat());
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).findById(order.getShopId());
    }

    @Test
    public void startOrderOnlineCollection() throws Exception {
        //given
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Set<Stock> stockItems = new HashSet<>();
        stockItems.add(new Stock("chips", 2, 10, 0, Collections.emptyList()));
        stockItems.add(new Stock("hotdog", 1, 20, 0, Collections.emptyList()));
        storeProfile.setStockList(stockItems);
        
        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        storeProfile.setHasVat(false);
        //create an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        Date date = Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
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
        Assert.assertNotNull(newOrder.getId());
        Assert.assertEquals(1.00, newOrder.getServiceFee(), 0);
        Assert.assertEquals(0, newOrder.getShippingData().getFee(), 0);
        Assert.assertEquals(40.00, newOrder.getBasketAmount(), 0);
        //verify total amount paid
        Assert.assertEquals(newOrder.getServiceFee() + basket.getItems().stream()
                .mapToDouble(BasketItem::getTotalPrice).sum() + shipping.getFee(), newOrder.getTotalAmount(), 0);
        Assert.assertFalse(newOrder.getHasVat());
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).findById(order.getShopId());

        System.out.println(order.getId());
    }

    @Test
    public void startOrderCollectionAllowed() throws Exception {
        //given
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);
        storeProfile.setCollectAllowed(false);

        Set<Stock> stockItems = new HashSet<>();
        stockItems.add(new Stock("chips", 2, 10, 0, Collections.emptyList()));
        stockItems.add(new Stock("hotdog", 1, 20, 0, Collections.emptyList()));
        storeProfile.setStockList(stockItems);

        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        storeProfile.setHasVat(false);
        //create an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        Date date = Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
        order.setShippingData(shipping);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");
        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        when(storeRepo.findById(order.getShopId())).thenReturn(Optional.of(storeProfile));

        try {
            Order newOrder = sut.startOrder(order);
            fail();
        }catch (Exception e) {
            Assert.assertEquals("Collection not allowed for shop name", e.getMessage());
        }
        verify(storeRepo).findById(order.getShopId());
        verify(customerRepo).existsById(order.getCustomerId());
    }

    @Test
    public void startOrderOnlineCollectionDateInThePast() throws Exception {
        //given
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Set<Stock> stockItems = new HashSet<>();
        stockItems.add(new Stock("chips", 2, 10, 0, Collections.emptyList()));
        stockItems.add(new Stock("hotdog", 1, 20, 0, Collections.emptyList()));
        storeProfile.setStockList(stockItems);

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
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        Date date = Date.from(LocalDateTime.now().minusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
        order.setShippingData(shipping);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        try {
            sut.startOrder(order);
            fail();
        } catch (Exception e) {
            Assert.assertEquals("Order shipping is null or pickup time or messenger not valid or shipping address not valid", e.getMessage());
        }
    }

    @Test
    public void startOrderStoreWithVAT() throws Exception {
        //given
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Set<Stock> stockItems = new HashSet<>();
        stockItems.add(new Stock("chips", 2, 10, 0, Collections.emptyList()));
        stockItems.add(new Stock("hotdog", 1, 20, 0, Collections.emptyList()));
        storeProfile.setStockList(stockItems);

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
        ShippingData shipping = new ShippingData("shopAddress",
                "46 Ududu Rd, Emlandweni, KwaMashu, 4051",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
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
        Assert.assertTrue(order.getHasVat());
        Assert.assertEquals(1.25, order.getServiceFee(), 0);
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
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setDescription("description");
        try {
            Order newOrder = sut.startOrder(order);
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue("order type is not valid".equals(e.getMessage()) ||
                    "Please supply shipping info for delivery or If you are paying in store, shipping should be null".equals(e.getMessage()));
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
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
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
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setCustomerId("customerId");
        order.setShopId(storeProfile.getId());
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
            Assert.assertEquals("Please supply shipping info for delivery or If you are paying in store, shipping should be null", e.getMessage());
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
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
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
            Assert.assertEquals("Please supply shipping info for delivery or If you are paying in store, shipping should be null", e.getMessage());
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
            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setOrderType(OrderType.ONLINE);
            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
            ShippingData shipping = new ShippingData();
            shipping.setFromAddress("address");
            shipping.setToAddress("address");
            shipping.setType(ShippingData.ShippingType.DELIVERY);
            shipping.setBuildingType(BuildingType.HOUSE);
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
            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY);
            shipping.setBuildingType(BuildingType.HOUSE);
            shipping.setMessengerId("messagerID");
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
            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY);
            shipping.setMessengerId("messagerID");
            shipping.setBuildingType(BuildingType.HOUSE);
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
            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY);
            shipping.setMessengerId("messagerID");
            shipping.setBuildingType(BuildingType.HOUSE);
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
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setShopId("shopid");
        order.setDescription("desc");

        StoreProfile shop = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);
        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0, Collections.emptyList());
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        shop.setStockList(stockList);
        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.paymentReceived(order)).thenReturn(true);
        when(repo.save(order)).thenReturn(order);
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(shop));
        when(deviceRepo.findByUserId(shop.getOwnerId())).thenReturn(storeDevices);
        when(deviceRepo.findByUserId(order.getShippingData().getMessengerId())).thenReturn(messengerDevices);

        Order finalOrder = sut.finishOder(order);
        //verify
        Assert.assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM, finalOrder.getStage());
        Assert.assertNotNull(finalOrder.getDescription());
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
    public void finishOderOnline_notify_by_sms() throws Exception {
        //given
        List<Device> storeDevices = Collections.emptyList();
        List<Device> messengerDevices = Collections.singletonList(new Device("token"));
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);

        //customer
        UserProfile customer = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setShopId("shopid");
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);
        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0, Collections.emptyList());
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        shop.setStockList(stockList);
        shop.setStoreWebsiteUrl("https://test.izinga.co.za");

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.paymentReceived(order)).thenReturn(true);
        when(repo.save(order)).thenReturn(order);
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(shop));
        when(deviceRepo.findByUserId(shop.getOwnerId())).thenReturn(storeDevices);
        when(customerRepo.findById(order.getCustomerId())).thenReturn(Optional.of(customer));
        when(deviceRepo.findByUserId(order.getShippingData().getMessengerId())).thenReturn(messengerDevices);

        Order finalOrder = sut.finishOder(order);

        //verify
        Assert.assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM, finalOrder.getStage());
        Assert.assertNotNull(finalOrder.getDescription());
        Assert.assertFalse(finalOrder.getShopPaid());
        Assert.assertFalse(order.getHasVat());
        verify(repo).save(order);
        verify(paymentService).paymentReceived(order);
        verify(repo).findById(order.getId());
        verify(storeRepo).findById(shopId);
        verify(storeRepo).save(shop);
        verify(pushNotificationService, times(0)).notifyStoreOrderPlaced(storeDevices, order);
        verify(pushNotificationService, times(0)).notifyStoreOrderPlaced(storeDevices, order);
        verify(smsNotifcation).notifyOrderPlaced(shop, order, customer);
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
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.INSTORE);
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId(shopId);
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);
        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0, Collections.emptyList());
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
        Assert.assertEquals(OrderStage.STAGE_7_ALL_PAID, finalOrder.getStage());
        Assert.assertNull(finalOrder.getShippingData());
        Assert.assertEquals(0, finalOrder.getServiceFee(), 0);
        Assert.assertTrue(finalOrder.getShopPaid());
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
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.INSTORE);
        order.setStage(OrderStage.STAGE_7_ALL_PAID);
        order.setShopPaid(true);
        order.setShopId(shopId);
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);
        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0, Collections.emptyList());
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        shop.setStockList(stockList);
        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.paymentReceived(order)).thenReturn(true);
        Order finalOrder = sut.finishOder(order);
        //verify
        Assert.assertEquals(OrderStage.STAGE_7_ALL_PAID, finalOrder.getStage());
        Assert.assertTrue(finalOrder.getShopPaid());
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
            
            
            ShippingData shipping = new ShippingData("shopAddress",
                    "to address",
                    ShippingData.ShippingType.DELIVERY);
            shipping.setMessengerId("messagerID");
            shipping.setBuildingType(BuildingType.HOUSE);
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
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setCustomerId(customerId);
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setShopId("shopid");
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);
        
        
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
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
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setCustomerId(customerId);
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
        order2.setCustomerId(customerId);
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);
        UserProfile initialProfile = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
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

        //customer
        UserProfile customer = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setShopId(shopId);
        order.setDescription("desc");
        List<String> tags = Collections.singletonList("Pizza");
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = createStoreProfile(StoreType.FOOD);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);
        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0, Collections.emptyList());
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        shop.setStockList(stockList);
        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.paymentReceived(order)).thenReturn(true);
        when(repo.save(order)).thenReturn(order);
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(shop));
        when(customerRepo.findById(order.getCustomerId())).thenReturn(Optional.of(customer));

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
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
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
        StoreProfile shop = createStoreProfile(StoreType.FOOD);

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
        when(deviceRepo.findByUserId(order.getShippingData().getMessengerId())).thenReturn(Collections.singletonList(device));
        Order finalOrder = sut.progressNextStage(order.getId());
        //verify
        Assert.assertEquals(OrderStage.STAGE_3_READY_FOR_COLLECTION, finalOrder.getStage());
        verify(deviceRepo).findByUserId(order.getShippingData().getMessengerId());
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
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.INSTORE);
        order.setStage(OrderStage.STAGE_7_ALL_PAID);
        order.setShopId(shopId);
        order.setDescription("desc");
        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        Order finalOrder = sut.progressNextStage(order.getId());
        //verify
        Assert.assertEquals(OrderStage.STAGE_7_ALL_PAID, finalOrder.getStage());
        verify(repo).findById(order.getId());
    }
    @Test
    public void progressLastStageOnlineDelivery() throws Exception {
        //given
        String shopId = "shopid";
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
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_7_ALL_PAID);
        order.setShopId(shopId);
        order.setDescription("desc");
        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        Order finalOrder = sut.progressNextStage(order.getId());
        //verify
        Assert.assertEquals(OrderStage.STAGE_7_ALL_PAID, finalOrder.getStage());
        verify(repo).findById(order.getId());
    }
    @Test
    public void progressStage6OnlineDelivery() throws Exception {
        //given
        String shopId = "shopid";
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
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_5_ARRIVED);
        order.setShopId(shopId);
        order.setDescription("desc");
        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(repo.save(order)).thenReturn(order);
        Order finalOrder = sut.progressNextStage(order.getId());
        //verify
        Assert.assertEquals(OrderStage.STAGE_7_ALL_PAID, finalOrder.getStage());
        verify(paymentService).completePaymentToMessenger(order);
        verify(repo).findById(order.getId());
    }

    @Test
    public void progressNextStageOnlineCollectionReady() throws Exception {
        //given
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setShopId(storeProfile.getId());
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
    public void progressNextStageOnlineCollection_CollectedBy_Customer() throws Exception {
        //given
        String shopId = "shopid";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile storeProfile = createStoreProfile(StoreType.FOOD);

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);


        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        order.setShopId(shopId);
        order.setDescription("desc");
        Device device = new Device("token");
        PushHeading title = new PushHeading("Food is ready for Collection at " + storeProfile.getName(),
                "Order Status Updated", null);
        PushMessage message = new PushMessage(PushMessageType.NEW_ORDER_UPDATE, title, order);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(repo.save(order)).thenReturn(order);

        Order finalOrder = sut.progressNextStage(order.getId());
        //verify
        Assert.assertEquals(OrderStage.STAGE_7_ALL_PAID, finalOrder.getStage());
        verify(repo).findById(order.getId());
        verify(repo).save(order);
        verify(paymentService, times(0)).completePaymentToMessenger(order);
    }

    @Test
    public void progressNextStageOnlineCollection() throws Exception {
        //given
        String shopId = "shopid";
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        Date orderDate = Date.from(LocalDateTime.now().minusSeconds(5).atZone(ZoneId.systemDefault()).toInstant());
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
        Assert.assertEquals(OrderStage.STAGE_7_ALL_PAID, finalOrder.getStage());
        verify(paymentService).completePaymentToShop(order);
        verify(repo).findById(order.getId());
        verify(repo).save(order);
    }
    @Test
    public void findOrderByStoreId() throws Exception {
        //given
        String shopId = "id of shop";
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
        order.setShopId(shopId);
        //order 2
        Order order2 = new Order();
        order.setBasket(basket);
        
        ShippingData shipping2 = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order2.setShippingData(shipping2);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order2.setShopId(shopId);
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = createStoreProfile(StoreType.FOOD);

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
        UserProfile patchProfileRequest = new UserProfile(
                "secondName",
                UserProfile.SignUpReason.BUY,
                "address2",
                "https://image.url2",
                "078mobilenumb",
                ProfileRoles.MESSENGER);
        patchProfileRequest.setId("messagerID");
        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId(patchProfileRequest.getId());
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
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order2.setShopId("shopid");
        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);
        //when
        when(repo.findByShippingDataMessengerIdAndStageNot(patchProfileRequest.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID)).thenReturn(orders);
        List<Order> finalOrder = sut.findOrderByMessengerId(patchProfileRequest.getId());
        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(2, finalOrder.size());
        Assert.assertEquals(patchProfileRequest.getId(),  finalOrder.get(0).getShippingData().getMessengerId());
        verify(repo).findByShippingDataMessengerIdAndStageNot(patchProfileRequest.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }
    @Test
    public void findOrderByStoreIdNoUpdaidOrdersRetuned() throws Exception {
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

        storeProfile.setBusinessHours(new ArrayList<>());
        storeProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        storeProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        storeProfile.setBank(bank);
        //when
        when(storeRepo.findById("shopid")).thenReturn(Optional.of(storeProfile));
        when(repo.findByShopIdAndStageNot(storeProfile.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID)).thenReturn(orders);
        List<Order> finalOrder = sut.findOrderByStoreId("shopid");
        //verify
        Assert.assertNotNull(finalOrder);
        Assert.assertEquals(1, finalOrder.size());
        finalOrder.forEach(data -> Assert.assertNotSame(data.getStage(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID));
        Assert.assertEquals("shopid", finalOrder.get(0).getShopId());
        verify(repo).findByShopIdAndStageNot(storeProfile.getId(), OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        verify(storeRepo).findById("shopid");
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
                ShippingData.ShippingType.COLLECTION);
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
                ShippingData.ShippingType.COLLECTION);
        Date pickUpTime = Date.from(LocalDateTime.now().minusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping2.setPickUpTime(pickUpTime);
        shipping.setMessengerId("messagerID");
        order2.setShippingData(shipping2);
        order2.setSmsSentToShop(true);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant());
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
        verify(repo).save(order);
        verify(smsNotifcation, times(0))
                .sendMessage(storeProfile.getMobileNumber(),
                        "Hello " + storeProfile.getName() + ", Please accept the order " + order.getId() +
                                " on iZinga app, otherwise the order will be cancelled.");
        verify(smsNotifcation, times(1))
                .sendMessage(phoneNumbers.get(0), "Hi, iZinga Admin. " + storeProfile.getName() + ", has not accepted order " + order.getId() +
                        " on iZinga app, otherwise the order will be cancelled.");
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
                ShippingData.ShippingType.COLLECTION);
        Date pickUpTime = Date.from(LocalDateTime.now().minusMinutes(320).atZone(ZoneId.systemDefault()).toInstant());
        shipping2.setPickUpTime(pickUpTime);
        shipping.setMessengerId("messagerID");
        order2.setShippingData(shipping2);
        order2.setSmsSentToShop(true);
        order2.setCustomerId("customer id");
        order2.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        Date orderDate2 = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant());
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
                ShippingData.ShippingType.COLLECTION);
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
        //order 1
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        
        
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.COLLECTION);
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
                ShippingData.ShippingType.COLLECTION);
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
                ShippingData.ShippingType.COLLECTION);
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
                ShippingData.ShippingType.COLLECTION);
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
}