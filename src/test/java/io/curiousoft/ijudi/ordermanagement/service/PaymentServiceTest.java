package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.notification.FirebaseNotificationService;
import io.curiousoft.ijudi.ordermanagement.repo.DeviceRepository;
import io.curiousoft.ijudi.ordermanagement.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
                0);

        //given an order
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
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
    public void completePaymentToShop() throws Exception {

        List<PaymentProvider> paymentProviders = new ArrayList<>();
        //adding all ukheshe services
        paymentProviders.add(ukheshePaymentProvider);
        paymentService = new PaymentService(pushNotificationService,
                paymentProviders, orderRepo, deviceRepo, storeRepository, 0);

        //given an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setFee(10);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId("shopid");
        order.setServiceFee(5.00);

        StoreProfile shop = new StoreProfile(
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
        when(ukheshePaymentProvider.makePayment(order, order.getBasketAmount())).thenReturn(true);
        when(deviceRepo.findByUserId(shop.getOwnerId())).thenReturn(Collections.singletonList(storeDevice));
        when(storeRepository.findById(order.getShopId())).thenReturn(Optional.of(shop));

        boolean received = paymentService.completePaymentToShop(order);

        //verify
        assertTrue(received);
        assertTrue(order.getShopPaid());
        assertNotNull(order.getPaymentType());
        verify(ukheshePaymentProvider).makePayment(order, order.getBasketAmount());
        verify(ukheshePaymentProvider).makePayment(order, 40);
        verify(deviceRepo).findByUserId(shop.getOwnerId());
        verify(storeRepository).findById(order.getShopId());
        verify(pushNotificationService).sendNotification(storeDevice, message);

    }

    @Test
    public void executePendingPayments() throws Exception {
        List<PaymentProvider> paymentProviders = new ArrayList<>();
        //adding all ukheshe services
        paymentProviders.add(ukheshePaymentProvider);
        paymentService = new PaymentService(pushNotificationService, paymentProviders,
                orderRepo, deviceRepo, storeRepository, 1);

        //given an order
        Order order = new Order();
        Basket basket = new Basket();
        order.setModifiedDate(Date.from(LocalDateTime.now().minusSeconds(6).atZone(ZoneId.systemDefault()).toInstant()));
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
        order.setShippingData(shipping);
        order.setPaymentType(PaymentType.UKHESHE);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        order.setShopId("shopid");

        List<Order> ordersList = Collections.singletonList(order);

        //when
        when(orderRepo.findByShopPaidAndStageAndModifiedDateBefore(eq(false), eq(OrderStage.STAGE_6_WITH_CUSTOMER),
                any(Date.class))).thenReturn(ordersList);
        when(ukheshePaymentProvider.getPaymentType()).thenReturn(PaymentType.UKHESHE);

        paymentService.processPendingPayments();

        //verify
        assertNotNull(order.getPaymentType());
        assertEquals(OrderStage.STAGE_7_PAID_SHOP, order.getStage());
        Assert.assertTrue(order.getShopPaid());
        verify(orderRepo).findByShopPaidAndStageAndModifiedDateBefore(eq(false), eq(OrderStage.STAGE_6_WITH_CUSTOMER), any(Date.class));
        verify(ukheshePaymentProvider).makePayment(ordersList.get(0), ordersList.get(0).getBasketAmount());
        verify(orderRepo).save(ordersList.get(0));

    }


    @Test
    public void paymentServiceNotConfigured() {

        List<PaymentProvider> paymentProviders = new ArrayList<>();
        //adding all ukheshe services
        paymentService = new PaymentService(pushNotificationService, paymentProviders, orderRepo, deviceRepo, storeRepository, 0);

        //given an order
        Order order = new Order();
        Basket basket = new Basket();
        order.setBasket(basket);
        Messager messenger = new Messager();
        messenger.setId("messagerID");
        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessenger(messenger);
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