package io.curiousoft.ijudi.ordermanagement.service.ozow;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.service.ukheshe.UkheshePaymentProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OzowPaymentProviderTest {

    private OzowPaymentProvider ozowPaymentProvider;
    @Mock
    private UkheshePaymentProvider ukheshePaymentProvider;

    @Before
    public void setUp() {
        ozowPaymentProvider = new OzowPaymentProvider("https://api.ozow.com",
                "760c42eec84640cf98aa1558135a9d90",
                ukheshePaymentProvider);
    }

    @Test
    public void paymentNotSuccessful() throws Exception {
        //given
        Order order = new Order();
        order.setId("1602528843");
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        BasketItem item = new BasketItem("bananas", 1, 1, 0);
        items.add(item);
        basket.setItems(items);
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setFee(2.05);
        order.setShippingData(shipping);
        order.setCreatedDate(UkheshePaymentProvider.dateFormat.parse("2020-05-22T15:07:27"));
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("ozow-46acdc67-adf0-480b-b5cc-c9e866d8e293");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setShopId("shopid");

        //when
        boolean received = ozowPaymentProvider.paymentReceived(order);

        //verify
        Assert.assertEquals(3.05, order.getTotalAmount(), 0);
        Assert.assertFalse(received);
    }

    @Ignore //FIXME
    @Test
    public void paymentSuccessful() throws Exception {
        //given
        Order order = new Order();
        order.setId("1602528843");
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        BasketItem item = new BasketItem("bananas", 1, 1, 0);
        items.add(item);
        basket.setItems(items);
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setFee(2.05);
        order.setShippingData(shipping);
        order.setCreatedDate(UkheshePaymentProvider.dateFormat.parse("2020-05-22T15:07:27"));
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("ozow-5480683c-d3d5-4f97-b3d4-57a3c597dbc7");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setShopId("shopid");

        //when
        boolean received = ozowPaymentProvider.paymentReceived(order);

        //verify
        Assert.assertEquals(3.05, order.getTotalAmount(), 0);
        Assert.assertFalse(received);
    }

    @Test
    public void makePaymentToShop() throws Exception {

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        BasketItem item = new BasketItem("bananas", 1, 1, 1);
        item.setName("item");
        item.setPrice(1);
        items.add(item);
        basket.setItems(items);
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setFee(10);
        order.setShippingData(shipping);
        order.setCreatedDate(UkheshePaymentProvider.dateFormat.parse("2020-05-22T15:07:27"));
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("Payment from 0812815707: order 606071520220511");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId("shopid");
        order.setServiceFee(5);
        order.setId(UUID.randomUUID().toString());

        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile shop = new StoreProfile(
                StoreType.FOOD,
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                null,
                "ownerId",
                new Bank());

        Bank bank = new Bank();
        bank.setAccountId("2885091160");
        bank.setName("ukheshe");
        bank.setPhone("phoneNumber");
        bank.setType("wallet");
        shop.setBank(bank);

        boolean paid = ozowPaymentProvider.makePaymentToShop(null, order, order.getBasketAmount());

        Assert.assertFalse(paid);
    }

    @Test
    public void makePaymentToMessenger() throws Exception {
        //given an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        BasketItem item = new BasketItem("bananas", 1, 1, 1);
        item.setName("item");
        item.setPrice(1);
        items.add(item);
        basket.setItems(items);
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setFee(10);
        order.setShippingData(shipping);
        order.setCreatedDate(UkheshePaymentProvider.dateFormat.parse("2020-05-22T15:07:27"));
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("Payment from 0812815707: order 606071520220511");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId("shopid");
        order.setServiceFee(5);
        order.setId(UUID.randomUUID().toString());

        //when
        ozowPaymentProvider.makePaymentToMessenger(order, order.getBasketAmount());

        //verify
        verify(ukheshePaymentProvider).makePaymentToMessenger(order, order.getBasketAmount());
    }
}