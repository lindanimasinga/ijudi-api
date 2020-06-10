package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PaymentVerifierTest {

    //system under test
    private PaymentVerifier paymentVerifier;
    @Mock
    PaymentService ukheshePaymentService;

    @Before
    public void setUp() {

    }

    @Test
    public void paymentReceived() throws Exception {

        List<PaymentService> paymentServices = new ArrayList<>();
        //adding all ukheshe services
        paymentServices.add(ukheshePaymentService);
        paymentVerifier = new PaymentVerifier(paymentServices);

        //given an order
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
        when(ukheshePaymentService.getPaymentType()).thenReturn(PaymentType.UKHESHE);
        when(ukheshePaymentService.paymentReceived(order)).thenReturn(true);

        boolean received = paymentVerifier.paymentReceived(order);

        //verify
        assertTrue(received);
        assertNotNull(order.getPaymentType());
        verify(ukheshePaymentService).paymentReceived(order);

    }

    @Test
    public void paymentServiceNotConfigured() {

        List<PaymentService> paymentServices = new ArrayList<>();
        //adding all ukheshe services
        paymentVerifier = new PaymentVerifier(paymentServices);

        //given an order
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
            boolean received = paymentVerifier.paymentReceived(order);
            assertNotNull(order.getPaymentType());
            fail();
        } catch (Exception e) {
            assertEquals("Your order has no ukheshe type set or the ukheshe provider for UKHESHE not configured on the server", e.getMessage());
        }

    }
}