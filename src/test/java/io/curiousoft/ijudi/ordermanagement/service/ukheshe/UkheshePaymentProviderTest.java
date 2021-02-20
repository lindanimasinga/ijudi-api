package io.curiousoft.ijudi.ordermanagement.service.ukheshe;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.text.ParseException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class UkheshePaymentProviderTest {

    public static final String PASSWORD = "111111";
    //sut
    private UkheshePaymentProvider ukheshePaymentProvider;

    @Mock
    StoreRepository storeRepository;
    @Mock
    UserProfileRepo userProfileRepo;

    @Test
    public void paymentReceivedInvalidCredentials() {

        //given an order
        String username = "username";
        String password = "password";
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentProvider = new UkheshePaymentProvider(baseUrl,
                username,
                customerId,
                password,
                mainAccount,
                storeRepository,
                userProfileRepo);

        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setShopId("shopid");

        //when
        try {
            boolean received = ukheshePaymentProvider.paymentReceived(order);
            fail();
        } catch (Exception e) {
            Assert.assertEquals("401 Unauthorized: [no body]", e.getMessage());
        }
    }


    @Ignore
    @Test
    public void paymentReceivedValidCredentials() throws Exception {

        String username = "0812815707";
        String password = PASSWORD;
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentProvider = new UkheshePaymentProvider(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository, userProfileRepo);

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

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setCreatedDate(UkheshePaymentProvider.dateFormat.parse("2020-05-22T15:07:27"));
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("Payment from 0812815707: order 606071520220511");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setShopId("shopid");

        //when

        boolean received = ukheshePaymentProvider.paymentReceived(order);

        //verify
        Assert.assertTrue(received);
        Assert.assertNotNull(ukheshePaymentProvider.getUkhesheAuthtoken());

    }

    @Test
    public void makePaymentToShopForOrder() throws Exception {

        String username = "0812815707";
        String password = PASSWORD;
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentProvider = new UkheshePaymentProvider(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository, userProfileRepo);

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

        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile shop = new StoreProfile(
                StoreType.FOOD,
                "name",
                "shortName",
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
        //when
        when(storeRepository.findById(order.getShopId())).thenReturn(Optional.of(shop));

        boolean paid = ukheshePaymentProvider.makePaymentToShop(shop, order, order.getBasketAmount());

        verify(storeRepository).findById(order.getShopId());
        Assert.assertTrue(paid);
    }

    @Test
    public void makePaymentToShopForOrderWithFreeDelivery() throws Exception {

        String username = "0812815707";
        String password = PASSWORD;
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentProvider = new UkheshePaymentProvider(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository, userProfileRepo);

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

        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile shop = new StoreProfile(
                StoreType.FOOD,
                "name",
                "shortName",
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
        //when
        when(storeRepository.findById(order.getShopId())).thenReturn(Optional.of(shop));

        boolean paid = ukheshePaymentProvider.makePaymentToShop(shop, order, order.getBasketAmount());

        verify(storeRepository).findById(order.getShopId());
        Assert.assertTrue(paid);
    }

    @Test
    public void makePaymentToShopForOrderPayByPhoneNumber() throws Exception {

        String username = "0812815707";
        String password = PASSWORD;
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentProvider = new UkheshePaymentProvider(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository, userProfileRepo);

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

        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile shop = new StoreProfile(
                StoreType.FOOD,
                "name",
                "shortName",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                null,
                "ownerId",
                new Bank());

        Bank bank = new Bank();
        bank.setName("ukheshe");
        bank.setPhone("0812815707");
        bank.setType("wallet");
        shop.setBank(bank);
        //when
        when(storeRepository.findById(order.getShopId())).thenReturn(Optional.of(shop));

        boolean paid = ukheshePaymentProvider.makePaymentToShop(shop, order, order.getBasketAmount());

        verify(storeRepository).findById(order.getShopId());
        Assert.assertTrue(paid);
    }

    @Test
    public void makePaymentToMessengerForOrder() throws Exception {

        String username = "0812815707";
        String password = PASSWORD;
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentProvider = new UkheshePaymentProvider(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository, userProfileRepo);

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

        List<String> tags = Collections.singletonList("Pizza");
        UserProfile messengerProfile = new UserProfile(
                "secondName",
                UserProfile.SignUpReason.BUY,
                "address2",
                "https://image.url2",
                "078mobilenumb",
                ProfileRoles.MESSENGER);
        messengerProfile.setId(order.getShippingData().getMessengerId());

        Bank bank = new Bank();
        bank.setAccountId("2885091160");
        bank.setName("ukheshe");
        bank.setPhone("phoneNumber");
        bank.setType("wallet");
        messengerProfile.setBank(bank);
        //when
        when(userProfileRepo.findById(order.getShippingData().getMessengerId())).thenReturn(Optional.of(messengerProfile));

        ukheshePaymentProvider.makePaymentToMessenger(order, order.getShippingData().getFee());

        verify(userProfileRepo).findById(order.getShippingData().getMessengerId());
    }

    @Test
    public void makePaymentToMessengerForOrder_payWithPhoneNumber() throws Exception {

        String username = "0812815707";
        String password = PASSWORD;
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "2885091160";

        ukheshePaymentProvider = new UkheshePaymentProvider(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository, userProfileRepo);

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

        List<String> tags = Collections.singletonList("Pizza");
        UserProfile messengerProfile = new UserProfile(
                "secondName",
                UserProfile.SignUpReason.BUY,
                "address2",
                "https://image.url2",
                "078mobilenumb",
                ProfileRoles.MESSENGER);
        messengerProfile.setId(order.getShippingData().getMessengerId());

        Bank bank = new Bank();
        bank.setName("ukheshe");
        bank.setPhone("0812815707");
        bank.setType("wallet");
        messengerProfile.setBank(bank);
        //when
        when(userProfileRepo.findById(order.getShippingData().getMessengerId())).thenReturn(Optional.of(messengerProfile));

        ukheshePaymentProvider.makePaymentToMessenger(order, order.getShippingData().getFee());

        verify(userProfileRepo).findById(order.getShippingData().getMessengerId());
    }

    @Test
    public void makePaymentToMessengerForOrder_NoMessengerExist() throws ParseException {

        String username = "0812815707";
        String password = PASSWORD;
        String baseUrl = "https://ukheshe-sandbox.jini.rocks/ukheshe-conductor/rest/v1";
        String customerId = "534";
        String mainAccount = "account";

        ukheshePaymentProvider = new UkheshePaymentProvider(
                baseUrl, username, customerId, password, mainAccount,
                storeRepository, userProfileRepo);

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

        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setCreatedDate(UkheshePaymentProvider.dateFormat.parse("2020-05-22T15:07:27"));
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("Payment from 0812815707: order 606071520220511");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId("shopid");

        try {
            ukheshePaymentProvider.makePaymentToMessenger(order, order.getBasketAmount());
            fail();
        } catch (Exception e) {
            Assert.assertEquals("Messenger does not exist", e.getMessage());
        }
    }
}