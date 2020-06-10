package io.curiousoft.ijudi.ordermanagement.service.ukheshe;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UkheshePaymentServiceTest {

    //sut
    private UkheshePaymentService ukheshePaymentService;

    @Mock
    StoreRepository storeRepository;

    @Test
    public void paymentReceivedInvalidCredentials() {

        //given an order
        String username = "username";
        String password = "password";
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentService = new UkheshePaymentService(baseUrl,
                username,
                customerId,
                password,
                mainAccount,
                storeRepository);

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

    @Ignore
    @Test
    public void paymentReceivedValidCredentials() throws Exception {

        String username = "0812815707";
        String password = "Csd0148()1";
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentService = new UkheshePaymentService(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository);

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

    @Test
    public void makePaymentForOrder() throws Exception {

        String username = "0812815707";
        String password = "Csd0148()1";
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentService = new UkheshePaymentService(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository);

        //given an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        BasketItem item = new BasketItem("bananas", 1, 1, 1);
        item.setName("item");
        item.setPrice(0);
        items.add(item);
        basket.setItems(items);
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY,
                0);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setDate(UkheshePaymentService.dateFormat.parse("2020-05-22T15:07:27"));
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("Payment from 0812815707: order 606071520220511");
        order.setCustomerId("customerId");
        order.setStage(2);
        order.setShopId("shopid");

        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile shop = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                "customer",
                null,
                "ownerId");

        Bank bank = new Bank();
        bank.setAccountId("2885091160");
        bank.setName("ukheshe");
        bank.setPhone("phoneNumber");
        bank.setType("wallet");
        shop.setBank(bank);
        //when
        when(storeRepository.findById(order.getShopId())).thenReturn(Optional.of(shop));

        boolean paid = ukheshePaymentService.makePayment(order);

        verify(storeRepository).findById(order.getShopId());
        Assert.assertTrue(paid);
    }

    @Test
    public void makePaymentForOrderNoShopExist() throws ParseException {

        String username = "0812815707";
        String password = "Csd0148()1";
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "account";

        ukheshePaymentService = new UkheshePaymentService(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository);

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

        try {
            boolean paid = ukheshePaymentService.makePayment(order);
            fail();
        } catch (Exception e) {
            Assert.assertEquals("shop does not exist", e.getMessage());
        }
    }
}