package io.curiousoft.ijudi.ordermanagent.service.order;

import io.curiousoft.ijudi.ordermanagent.model.*;
import io.curiousoft.ijudi.ordermanagent.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagent.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagent.repo.UserProfileRepo;
import io.curiousoft.ijudi.ordermanagent.service.OrderServiceImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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

    //system under test
    private OrderServiceImpl sut;

    @Before
    public void setUp() throws Exception {
        sut = new OrderServiceImpl(repo, storeRepo, customerRepo);
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

        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        when(storeRepo.existsById(order.getShopId())).thenReturn(true);
        when(repo.save(order)).thenReturn(order);

        Order newOrder = sut.startOrder(order);

        //verify
        Assert.assertEquals(0, newOrder.getStage());
        Assert.assertNotNull(order.getId());
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

            //when

            Order newOrder = sut.startOrder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order shipping is not valid", e.getMessage());
        }
    }

    @Test
    public void finishOrderShopNotExist() throws Exception {

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

        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        try {
            Order newOrder = sut.finishOder(order);
            fail();
        } catch (Exception e) {
            //verify
            verifyNoInteractions(repo);
            verify(storeRepo).existsById(order.getShopId());
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

            //when

            Order newOrder = sut.finishOder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order shipping is not valid", e.getMessage());
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
        order.setStage(2);
        order.setShopId("shopid");

        //when
        when(customerRepo.existsById(order.getCustomerId())).thenReturn(true);
        when(storeRepo.existsById(order.getShopId())).thenReturn(true);
        when(repo.save(order)).thenReturn(order);
        Order finalOrder = sut.finishOder(order);

        //verify
        Assert.assertEquals(3, finalOrder.getStage());
        verify(repo).save(order);
        verify(customerRepo).existsById(order.getCustomerId());
        verify(storeRepo).existsById(order.getShopId());

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

            //when

            Order newOrder = sut.finishOder(order);

            //verify
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order basket is not valid", e.getMessage());
        }
    }
}