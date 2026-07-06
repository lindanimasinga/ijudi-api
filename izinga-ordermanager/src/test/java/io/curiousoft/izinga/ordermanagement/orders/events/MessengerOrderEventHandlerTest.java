package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.messaging.AdminOnlyNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.promocodes.PromoCodeClient;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.recon.payout.MessengerPayout;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessengerOrderEventHandlerTest {

    @Mock private FirebaseNotificationService pushNotificationService;
    @Mock private AdminOnlyNotificationService smsNotificationService;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private DeviceService deviceService;
    @Mock private UserProfileService userProfileService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ReconService reconService;
    @Mock private PromoCodeClient promoCodeClient;
    @Mock private OrderRepository orderRepository;

    private MessengerOrderEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MessengerOrderEventHandler(
                pushNotificationService,
                smsNotificationService,
                emailNotificationService,
                deviceService,
                userProfileService,
                eventPublisher,
                reconService,
                promoCodeClient,
                orderRepository,
                0.10
        );
    }

    private Order makeOrder(String orderId, String messengerId) {
        ShippingData shippingData = new ShippingData();
        shippingData.setMessengerId(messengerId);
        shippingData.setDeliveryFee(50.0);
        shippingData.setType(ShippingData.ShippingType.DELIVERY);
        Basket basket = new Basket();
        Order order = new Order();
        order.setId(orderId);
        order.setShopId("test-shop-id");
        order.setShippingData(shippingData);
        order.setBasket(basket);
        order.setStage(OrderStage.STAGE_7_ALL_PAID);
        order.addStatusHistory(OrderStage.STAGE_7_ALL_PAID);
        return order;
    }

    private UserProfile makeDriver(String driverId, String ambassadorId, boolean firstDeliveryCompleted) {
        UserProfile driver = new UserProfile(
                "Driver Name", UserProfile.SignUpReason.DELIVERY_DRIVER,
                "1 Driver St", "img.jpg", "0821111111", ProfileRoles.MESSENGER
        );
        driver.setId(driverId);
        driver.setAmbassadorId(ambassadorId);
        driver.setFirstDeliveryCompleted(firstDeliveryCompleted);
        Bank bank = new Bank();
        bank.setName("FNB");
        bank.setAccountId("0821111111");
        bank.setType(BankAccType.EWALLET);
        bank.setBranchCode("250655");
        driver.setBank(bank);
        driver.setEmailAddress("driver@mail.com");
        return driver;
    }

    private UserProfile makeAmbassador(String ambassadorId) {
        UserProfile ambassador = new UserProfile(
                "Ambassador Name", UserProfile.SignUpReason.DELIVERY_DRIVER,
                "2 Amb St", "img.jpg", "0829999999", ProfileRoles.AMBASSADOR
        );
        ambassador.setId(ambassadorId);
        Bank bank = new Bank();
        bank.setName("FNB");
        bank.setAccountId("0829999999");
        bank.setType(BankAccType.EWALLET);
        bank.setBranchCode("250655");
        ambassador.setBank(bank);
        ambassador.setEmailAddress("ambassador@mail.com");
        return ambassador;
    }

    @Test
    void handleOrderUpdatedEvent_stage7_triggersAmbassadorCommission_whenEligible() throws Exception {
        String driverId = "driver-001";
        String ambassadorId = "amb-001";
        Order order = makeOrder("order-001", driverId);
        UserProfile driver = makeDriver(driverId, ambassadorId, false);
        UserProfile ambassador = makeAmbassador(ambassadorId);
        StoreProfile store = mock(StoreProfile.class);

        MessengerPayout messengerPayout = mock(MessengerPayout.class);
        when(messengerPayout.isPermEmployed()).thenReturn(false);
        when(messengerPayout.getTotal()).thenReturn(BigDecimal.valueOf(100.0));

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);

        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(messengerPayout);
        when(userProfileService.find(ambassadorId)).thenReturn(ambassador);
        when(reconService.generatePayoutForAmbassadorAndOrder(any(), eq(ambassador))).thenReturn(null);
        when(userProfileService.update(eq(driverId), any())).thenReturn(driver);

        handler.handleOrderUpdatedEvent(event);

        verify(reconService).generatePayoutForAmbassadorAndOrder(any(), eq(ambassador));
        verify(userProfileService).update(eq(driverId), argThat(d -> d.getFirstDeliveryCompleted()));
    }

    @Test
    void handleOrderUpdatedEvent_stage7_skipsAmbassadorCommission_whenFirstDeliveryAlreadyCompleted() throws Exception {
        String driverId = "driver-002";
        String ambassadorId = "amb-002";
        Order order = makeOrder("order-002", driverId);
        UserProfile driver = makeDriver(driverId, ambassadorId, true);
        StoreProfile store = mock(StoreProfile.class);

        MessengerPayout messengerPayout = mock(MessengerPayout.class);
        when(messengerPayout.isPermEmployed()).thenReturn(false);
        when(messengerPayout.getTotal()).thenReturn(BigDecimal.valueOf(100.0));

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);
        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(messengerPayout);

        handler.handleOrderUpdatedEvent(event);

        verify(reconService, never()).generatePayoutForAmbassadorAndOrder(any(), any());
        verify(userProfileService, never()).update(any(), any());
    }

    @Test
    void handleOrderUpdatedEvent_stage7_skipsAmbassadorCommission_whenNoAmbassadorId() throws Exception {
        String driverId = "driver-003";
        Order order = makeOrder("order-003", driverId);
        UserProfile driver = makeDriver(driverId, null, false);
        StoreProfile store = mock(StoreProfile.class);

        MessengerPayout messengerPayout = mock(MessengerPayout.class);
        when(messengerPayout.isPermEmployed()).thenReturn(false);
        when(messengerPayout.getTotal()).thenReturn(BigDecimal.valueOf(100.0));

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);
        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(messengerPayout);

        handler.handleOrderUpdatedEvent(event);

        verify(reconService, never()).generatePayoutForAmbassadorAndOrder(any(), any());
    }

    @Test
    void handleOrderUpdatedEvent_stage7_skipsAmbassadorCommission_whenAmbassadorNotFound() throws Exception {
        String driverId = "driver-004";
        String ambassadorId = "amb-404";
        Order order = makeOrder("order-004", driverId);
        UserProfile driver = makeDriver(driverId, ambassadorId, false);
        StoreProfile store = mock(StoreProfile.class);

        MessengerPayout messengerPayout = mock(MessengerPayout.class);
        when(messengerPayout.isPermEmployed()).thenReturn(false);
        when(messengerPayout.getTotal()).thenReturn(BigDecimal.valueOf(100.0));

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);
        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(messengerPayout);
        when(userProfileService.find(ambassadorId)).thenReturn(null);

        handler.handleOrderUpdatedEvent(event);

        verify(reconService, never()).generatePayoutForAmbassadorAndOrder(any(), any());
        verify(userProfileService, never()).update(any(), any());
    }

    @Test
    void handleOrderUpdatedEvent_stage7_skipsAmbassadorCommission_whenDriverHasNoAmbassadorAndNoFirstDelivery() throws Exception {
        // Driver exists with no ambassadorId and firstDeliveryCompleted=false — still no ambassador commission
        String driverId = "driver-005";
        Order order = makeOrder("order-005", driverId);
        UserProfile driver = makeDriver(driverId, null, false); // no ambassador set
        StoreProfile store = mock(StoreProfile.class);

        MessengerPayout messengerPayout = mock(MessengerPayout.class);
        when(messengerPayout.isPermEmployed()).thenReturn(false);
        when(messengerPayout.getTotal()).thenReturn(BigDecimal.valueOf(100.0));

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);
        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(messengerPayout);
        // smsNotificationService.sendDriverOrderCompletedMessage is lenient

        handler.handleOrderUpdatedEvent(event);

        verify(reconService, never()).generatePayoutForAmbassadorAndOrder(any(), any());
        verify(userProfileService, never()).update(any(), any());
    }
}
