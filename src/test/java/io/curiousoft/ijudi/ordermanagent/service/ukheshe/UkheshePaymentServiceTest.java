package io.curiousoft.ijudi.ordermanagent.service.ukheshe;

import io.curiousoft.ijudi.ordermanagent.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class UkheshePaymentServiceTest {

    //sut
    private UkheshePaymentService ukheshePaymentService;

    @Test
    public void paymentReceivedInvalidCredentials() {

        //given an order
        String username = "username";
        String password = "password";
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";

        ukheshePaymentService = new UkheshePaymentService(baseUrl, username, customerId, password);

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
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(2);
        order.setShopId("shopid");

        //when
        try {
            boolean received = ukheshePaymentService.paymentReceived(order);
            fail();
        } catch (Exception e) {
            Assert.assertEquals("401 Unauthorized: [no body]", e.getMessage());
        }
    }

    @Test
    public void paymentReceivedValidCredentials() throws Exception {

        String username = "0812815707";
        String password = "Csd0148()1";
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        ukheshePaymentService = new UkheshePaymentService(baseUrl, username, customerId, password);

        //given an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        BasketItem item = new BasketItem("bananas", 1, 1, 1);
        item.setName("item");
        item.setPrice(25);
        items.add(item);
        basket.setItems(items);
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY,
                10);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setDate(UkheshePaymentService.dateFormat.parse("2020-05-22T15:07:27"));
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("Payment from 0812815707: order 606071520220511");
        order.setCustomerId("customerId");
        order.setStage(2);
        order.setShopId("shopid");

        //when

        boolean received = ukheshePaymentService.paymentReceived(order);

        //verify
        Assert.assertTrue(received);
        Assert.assertNotNull(ukheshePaymentService.getUkhesheAuthtoken());

    }
}