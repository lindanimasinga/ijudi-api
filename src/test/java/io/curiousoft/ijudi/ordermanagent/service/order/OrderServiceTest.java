package io.curiousoft.ijudi.ordermanagent.service.order;

import io.curiousoft.ijudi.ordermanagent.model.*;
import io.curiousoft.ijudi.ordermanagent.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagent.service.OrderServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository repo;

    //system under test
    private OrderServiceImpl sut;

    @Test
    public void startOrder() throws Exception {

        sut = new OrderServiceImpl(repo);

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

        Customer customer = new Customer();
        customer.setId("id");
        order.setCustomer(customer);
        order.setStage(0);

        //when

        when(repo.save(order)).thenReturn(order);

        Order newOrder = sut.startOrder(order);

        //verify
        Assert.assertEquals(0, newOrder.getStage());
        Assert.assertNotNull(order.getId());
        verify(repo).save(order);
    }

    @Test
    public void startOrderNoShippingData() {

        try {
            sut = new OrderServiceImpl(repo);

            //given
            Order order = new Order();
            Basket basket = new Basket();
            order.setBasket(basket);

            UserProfile messenger = new UserProfile("testId",
                    "99091111222323",
                    "testName",
                    "41 Sheffs, Afr, 8009",
                    "Https://url.com",
                    "08122344531112",
                    "customer");

            Customer customer = new Customer();
            customer.setId("id");
            order.setCustomer(customer);
            order.setStage(0);

            //when

            Order newOrder = sut.startOrder(order);

            //verify
            Assert.fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order shipping is not valid", e.getMessage());
        }
    }

    @Test
    public void startOrderNoCustomer() {

        try {
            sut = new OrderServiceImpl(repo);

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

            //when

            Order newOrder = sut.startOrder(order);

            //verify
            Assert.fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order customer is not valid", e.getMessage());
        }
    }

    @Test
    public void startOrderNoBasket() {

        try {
            sut = new OrderServiceImpl(repo);

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

            Customer customer = new Customer();
            customer.setId("id");
            order.setCustomer(customer);
            order.setStage(0);

            //when

            Order newOrder = sut.startOrder(order);

            //verify
            Assert.fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order basket is not valid", e.getMessage());
        }
    }

    @Test
    public void finishOder() throws Exception {

        //given
        sut = new OrderServiceImpl(repo);

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

        Customer customer = new Customer();
        customer.setId("id");
        order.setCustomer(customer);
        order.setStage(2);

        //when
        when(repo.save(order)).thenReturn(order);
        Order finalOrder = sut.finishOder(order);

        //verify
        Assert.assertEquals(3, finalOrder.getStage());
        verify(repo).save(order);

    }

    @Test
    public void finishOrderNoBasket() {

        try {
            sut = new OrderServiceImpl(repo);

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

            Customer customer = new Customer();
            customer.setId("id");
            order.setCustomer(customer);
            order.setStage(0);

            //when

            Order newOrder = sut.finishOder(order);

            //verify
            Assert.fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("order basket is not valid", e.getMessage());
        }
    }
}