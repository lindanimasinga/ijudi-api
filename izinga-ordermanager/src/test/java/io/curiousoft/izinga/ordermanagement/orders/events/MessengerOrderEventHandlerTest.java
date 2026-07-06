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

    private UserProfile makeDriver(String driverId) {
        UserProfile driver = new UserProfile(
                "Driver Name", UserProfile.SignUpReason.DELIVERY_DRIVER,
                "1 Driver St", "img.jpg", "0821111111", ProfileRoles.MESSENGER
        );
        driver.setId(driverId);
        Bank bank = new Bank();
        bank.setName("FNB");
        bank.setAccountId("0821111111");
        bank.setType(BankAccType.EWALLET);
        bank.setBranchCode("250655");
        driver.setBank(bank);
        driver.setEmailAddress("driver@mail.com");
        return driver;
    }

    @Test
    void handleOrderUpdatedEvent_stage7_generatesMessengerPayoutAndNotifiesDriver() throws IOException {
        String driverId = "driver-001";
        Order order = makeOrder("order-001", driverId);
        UserProfile driver = makeDriver(driverId);
        StoreProfile store = mock(StoreProfile.class);

        MessengerPayout messengerPayout = mock(MessengerPayout.class);
        when(messengerPayout.isPermEmployed()).thenReturn(false);
        when(messengerPayout.getTotal()).thenReturn(BigDecimal.valueOf(100.0));

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);
        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(messengerPayout);

        handler.handleOrderUpdatedEvent(event);

        verify(reconService).generatePayoutForMessengerAndOrder(any());
        verify(smsNotificationService).sendDriverOrderCompletedMessage(eq(driver), eq("order-001"), any(), any());
    }

    @Test
    void handleOrderUpdatedEvent_stage7_permEmployedDriver_skipsDriverNotification() throws IOException {
        String driverId = "driver-002";
        Order order = makeOrder("order-002", driverId);
        UserProfile driver = makeDriver(driverId);
        StoreProfile store = mock(StoreProfile.class);

        MessengerPayout messengerPayout = mock(MessengerPayout.class);
        when(messengerPayout.isPermEmployed()).thenReturn(true);

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);
        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(messengerPayout);

        handler.handleOrderUpdatedEvent(event);

        verify(smsNotificationService, never()).sendDriverOrderCompletedMessage(any(), any(), any(), any());
    }

    @Test
    void handleOrderUpdatedEvent_stage7_nullPayout_skipsDriverNotification() throws IOException {
        String driverId = "driver-003";
        Order order = makeOrder("order-003", driverId);
        UserProfile driver = makeDriver(driverId);
        StoreProfile store = mock(StoreProfile.class);

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);
        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(null);

        handler.handleOrderUpdatedEvent(event);

        verify(smsNotificationService, never()).sendDriverOrderCompletedMessage(any(), any(), any(), any());
    }

    @Test
    void handleOrderUpdatedEvent_stage7_stillGeneratesMessengerPayoutEvenWhenProfileNotFound() throws IOException {
        // The guard is on event.getMessenger() (String), which OrderUpdatedEvent requires non-null.
        // So generatePayoutForMessengerAndOrder is still called; payout notification is skipped.
        String unknownDriverId = "unknown-driver";
        Order order = makeOrder("order-004", unknownDriverId);
        StoreProfile store = mock(StoreProfile.class);

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, unknownDriverId, store);
        when(userProfileService.find(unknownDriverId)).thenReturn(null);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(null);

        handler.handleOrderUpdatedEvent(event);

        verify(reconService).generatePayoutForMessengerAndOrder(any());
        verify(smsNotificationService, never()).sendDriverOrderCompletedMessage(any(), any(), any(), any());
    }

    @Test
    void handleOrderUpdatedEvent_stage7_doesNotTriggerAmbassadorCommission() throws IOException {
        // Ambassador commission is no longer triggered from this handler — verify reconService
        // never receives a generatePayoutForAmbassadorAndApproval call from here
        String driverId = "driver-005";
        Order order = makeOrder("order-005", driverId);
        UserProfile driver = makeDriver(driverId);
        driver.setAmbassadorId("amb-001");
        StoreProfile store = mock(StoreProfile.class);

        MessengerPayout messengerPayout = mock(MessengerPayout.class);
        when(messengerPayout.isPermEmployed()).thenReturn(false);
        when(messengerPayout.getTotal()).thenReturn(BigDecimal.valueOf(100.0));

        OrderUpdatedEvent event = new OrderUpdatedEvent(this, order, driverId, store);
        when(userProfileService.find(driverId)).thenReturn(driver);
        when(reconService.generatePayoutForMessengerAndOrder(any())).thenReturn(messengerPayout);

        handler.handleOrderUpdatedEvent(event);

        verify(reconService, never()).generatePayoutForAmbassadorAndApproval(any(), any());
    }
}
