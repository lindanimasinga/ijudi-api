package io.curiousoft.izinga.ordermanagement.service.paymentverify;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.notification.FirebaseNotificationService;
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