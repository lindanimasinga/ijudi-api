package io.curiousoft.ijudi.ordermanagent.service.order;

import io.curiousoft.ijudi.ordermanagent.model.*;
import io.curiousoft.ijudi.ordermanagent.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagent.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagent.repo.UserProfileRepo;
import io.curiousoft.ijudi.ordermanagent.service.OrderServiceImpl;
import io.curiousoft.ijudi.ordermanagent.service.PaymentVerifier;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
    private PaymentVerifier paymentVerifier;

    //system under test
    private OrderServiceImpl sut;

    @Before
    public void setUp() throws Exception {
        sut = new OrderServiceImpl(repo,
                storeRepo,
                customerRepo,
                paymentVerifier);
    }

    @Test
    public void startOrder() throws Exception {

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
        order.setStage(0);
        order.setDescription("description");

        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        when(storeRepo.existsById(order.getShopId())).thenReturn(true);
        when(repo.save(order)).thenReturn(order);

        Order newOrder = sut.startOrder(order);

        //verify
        Assert.assertEquals(0, newOrder.getStage());
        Assert.assertNotNull(order.getId());
        Assert.assertNotNull(order.getDate());
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).existsById(order.getShopId());
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

        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(0);
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
        order.setStage(0);
        order.setDescription("description");

        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        try {
            Order newOrder = sut.startOrder(order);
            fail();
        } catch (Exception e) {
            //verify
            verifyNoInteractions(repo);
            verify(storeRepo).existsById(order.getShopId());
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
                    "messanger");

            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setStage(0);
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

        order.setCustomerId("customerId");
        order.setShopId("shopid");
        order.setStage(0);
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
                    "messanger");

            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setStage(0);
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
                    "messanger");

            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setStage(0);
            order.setShippingData(new ShippingData());
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

            order.setStage(0);
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

            order.setStage(0);
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
            order.setStage(0);
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
    public void finishOder() throws Exception {

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
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(2);
        order.setShopId(shopId);
        order.setDescription("desc");

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                "customer",
                businessHours);
        shop.setBusinessHours(new ArrayList<>());
        shop.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        shop.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0);
        List<Stock> stockList = new ArrayList<>();
        stockList.add(stock1);
        shop.setStockList(stockList);

        //when
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentVerifier.paymentReceived(order)).thenReturn(true);
        when(repo.save(order)).thenReturn(order);
        when(storeRepo.findById(shopId)).thenReturn(Optional.of(shop));

        Order finalOrder = sut.finishOder(order);

        //verify
        Assert.assertEquals(1, finalOrder.getStage());
        Assert.assertNotNull(finalOrder.getDescription());
        verify(repo).save(order);
        verify(paymentVerifier).paymentReceived(order);
        verify(repo).findById(order.getId());
        verify(storeRepo).findById(shopId);
        verify(storeRepo).save(shop);

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

            order.setCustomerId("customerId");
            order.setShopId("shopid");
            order.setStage(0);
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
        order.setStage(2);
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
        order2.setStage(2);
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
        order.setStage(2);
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
        order2.setStage(2);
        order2.setShopId("shopid");

        ArrayList<Order> orders = new ArrayList<>();
        orders.add(order);
        orders.add(order2);

        UserProfile initialProfile = new UserProfile(
                "name",
                "address",
                "https://image.url",
                phoneNumber,
                "customer");
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
}