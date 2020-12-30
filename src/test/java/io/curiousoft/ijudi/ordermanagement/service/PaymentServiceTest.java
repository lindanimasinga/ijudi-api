package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.notification.FirebaseNotificationService;
import io.curiousoft.ijudi.ordermanagement.repo.DeviceRepository;
import io.curiousoft.ijudi.ordermanagement.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PaymentServiceTest {

    //system under test
    private PaymentService paymentService;
    @Mock
    PaymentProvider ukheshePaymentProvider;
    @Mock
    OrderRepository orderRepo;
    @Mock
    FirebaseNotificationService pushNotificationService;
    @Mock
    private DeviceRepository deviceRepo;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserProfileRepo userProfileRepo;

    @Before
    public void setUp() {

    }

    @Test
    public void paymentReceived() throws Exception {

        List<PaymentProvider> paymentProviders = new ArrayList<>();
        //adding all ukheshe services
        paymentProviders.add(ukheshePaymentProvider);
        paymentService = new PaymentService(pushNotificationService,
                paymentProviders,
                orderRepo,
                deviceRepo,
                storeRepository,
                userProfileRepo,
                0,
                0.1);

        //given an order
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
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setShopId("shopid");

        //when
        when(ukheshePaymentProvider.getPaymentType()).thenReturn(PaymentType.UKHESHE);
        when(ukheshePaymentProvider.paymentReceived(order)).thenReturn(true);

        boolean received = paymentService.paymentReceived(order);

        //verify
        assertTrue(received);
        assertNotNull(order.getPaymentType());
        verify(ukheshePaymentProvider).paymentReceived(order);

    }

    @Test
    public void completePaymentToShop_no_commission_for_izinga() throws Exception {

        List<PaymentProvider> paymentProviders = new ArrayList<>();
        //adding all ukheshe services
        paymentProviders.add(ukheshePaymentProvider);
        paymentService = new PaymentService(pushNotificationService,
                paymentProviders, orderRepo, deviceRepo, storeRepository,
                userProfileRepo,0, 0.1);

        //given an order
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
        shipping.setFee(10);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId("shopid");
        order.setServiceFee(5.00);

        StoreProfile shop = new StoreProfile(
                StoreType.FOOD,
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                null,
                null,
                "ownerId",
                new Bank());;

        Device storeDevice = new Device("token");
        String content = "Payment of R " + order.getBasketAmount() + " received";
        PushHeading heading = new PushHeading("Payment of R " + order.getBasketAmount() + " received",
                "Order Payment Received", null);
        PushMessage message = new PushMessage(PushMessageType.PAYMENT, heading, content);

        //when
        when(ukheshePaymentProvider.getPaymentType()).thenReturn(PaymentType.UKHESHE);
        when(ukheshePaymentProvider.makePaymentToShop(shop, order, order.getBasketAmount())).thenReturn(true);
        when(deviceRepo.findByUserId(shop.getOwnerId())).thenReturn(Collections.singletonList(storeDevice));
        when(storeRepository.findById(order.getShopId())).thenReturn(Optional.of(shop));

        boolean received = paymentService.completePaymentToShop(order);

        //verify
        assertTrue(received);
        assertTrue(order.getShopPaid());
        assertNotNull(order.getPaymentType());
        verify(ukheshePaymentProvider).makePaymentToShop(shop, order, order.getBasketAmount());
        verify(ukheshePaymentProvider).makePaymentToShop(shop, order, 40);
        verify(deviceRepo).findByUserId(shop.getOwnerId());
        verify(storeRepository).findById(order.getShopId());
        verify(pushNotificationService).sendNotification(storeDevice, message);

    }

    @Test
    public void completePaymentToShop_commission_for_izinga() throws Exception {

        List<PaymentProvider> paymentProviders = new ArrayList<>();
        //adding all ukheshe services
        paymentProviders.add(ukheshePaymentProvider);
        paymentService = new PaymentService(pushNotificationService,
                paymentProviders, orderRepo, deviceRepo, storeRepository,
                userProfileRepo,0, 0.1);

        //given an order
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
        shipping.setFee(10);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId("shopid");
        order.setServiceFee(5.00);

        StoreProfile shop = new StoreProfile(
                StoreType.FOOD,
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                null,
                null,
                "ownerId",
                new Bank());;
        shop.setIzingaTakesCommission(true);

        Device storeDevice = new Device("token");
        String content = "Payment of R " + order.getBasketAmount() * 0.9 + " received";
        PushHeading heading = new PushHeading("Payment of R " + order.getBasketAmount() * 0.9 + " received",
                "Order Payment Received", null);
        PushMessage message = new PushMessage(PushMessageType.PAYMENT, heading, content);

        //when
        when(ukheshePaymentProvider.getPaymentType()).thenReturn(PaymentType.UKHESHE);
        when(ukheshePaymentProvider.makePaymentToShop(shop, order, order.getBasketAmount() * 0.9)).thenReturn(true);
        when(deviceRepo.findByUserId(shop.getOwnerId())).thenReturn(Collections.singletonList(storeDevice));
        when(storeRepository.findById(order.getShopId())).thenReturn(Optional.of(shop));

        boolean received = paymentService.completePaymentToShop(order);

        //verify
        assertTrue(received);
        assertTrue(order.getShopPaid());
        assertNotNull(order.getPaymentType());
        verify(ukheshePaymentProvider).makePaymentToShop(shop, order, order.getBasketAmount() * 0.9);
        verify(ukheshePaymentProvider).makePaymentToShop(shop, order, 36);
        verify(deviceRepo).findByUserId(shop.getOwnerId());
        verify(storeRepository).findById(order.getShopId());
        verify(pushNotificationService).sendNotification(storeDevice, message);

    }

    @Test
    public void completePaymentToMessenger() throws Exception {

        List<PaymentProvider> paymentProviders = new ArrayList<>();
        //adding all ukheshe services
        paymentProviders.add(ukheshePaymentProvider);
        paymentService = new PaymentService(pushNotificationService,
                paymentProviders, orderRepo, deviceRepo, storeRepository,userProfileRepo, 0, 0.1);

        //given an order
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
        shipping.setFee(10);
        shipping.setMessengerId("messagerID");
        order.setShippingData(shipping);
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId("shopid");
        order.setShopPaid(true);
        order.setServiceFee(5.00);

        UserProfile messengerProfile = new UserProfile(
                "secondName",
                UserProfile.SignUpReason.BUY,
                "address2",
                "https://image.url2",
                "078mobilenumb",
                ProfileRoles.MESSENGER);
        messengerProfile.setId(order.getShippingData().getMessengerId());

        Bank bank = new Bank();
        bank.setPhone("0812823345");
        bank.setType("ukheshe");
        messengerProfile.setBank(bank);

        Device storeDevice = new Device("token");
        String content = "Payment of R " + order.getShippingData().getFee() + " received";
        PushHeading heading = new PushHeading("Payment of R " + order.getShippingData().getFee() + " received",
                "Order Payment Received", null);
        PushMessage message = new PushMessage(PushMessageType.PAYMENT, heading, content);

        //when
        when(ukheshePaymentProvider.getPaymentType()).thenReturn(PaymentType.UKHESHE);
        when(deviceRepo.findByUserId(messengerProfile.getId())).thenReturn(Collections.singletonList(storeDevice));

        paymentService.completePaymentToMessenger(order);

        //verify
        assertTrue(order.getShopPaid());
        assertTrue(order.getMessengerPaid());
        assertNotNull(order.getPaymentType());
        verify(ukheshePaymentProvider).makePaymentToMessenger(order, order.getShippingData().getFee());
        verify(ukheshePaymentProvider).makePaymentToMessenger(order, 10);
        verify(deviceRepo).findByUserId(order.getShippingData().getMessengerId());
        verify(pushNotificationService).sendNotification(storeDevice, message);
    }


    @Test
    public void paymentServiceNotConfigured() {

        List<PaymentProvider> paymentProviders = new ArrayList<>();
        //adding all ukheshe services
        paymentService = new PaymentService(pushNotificationService,
                paymentProviders, orderRepo, deviceRepo,
                storeRepository,userProfileRepo, 0, 0.1);

        //given an order
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
            boolean received = paymentService.paymentReceived(order);
            assertNotNull(order.getPaymentType());
            fail();
        } catch (Exception e) {
            assertEquals("Your order has no ukheshe type set or the ukheshe provider for UKHESHE not configured on the server", e.getMessage());
        }

    }
}