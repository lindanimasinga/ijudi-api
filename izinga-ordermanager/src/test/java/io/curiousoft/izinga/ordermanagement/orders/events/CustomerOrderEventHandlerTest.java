package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerOrderEventHandlerTest {

    @Mock private FirebaseNotificationService pushNotificationService;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private WhatsappNotificationService whatsappNotificationService;
    @Mock private DeviceService deviceService;
    @Mock private UserProfileService userProfileService;

    private CustomerOrderEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomerOrderEventHandler(
                pushNotificationService,
                emailNotificationService,
                whatsappNotificationService,
                deviceService,
                userProfileService
        );
    }

    private UserProfile customer() {
        UserProfile profile = new UserProfile(
                "John Doe",
                UserProfile.SignUpReason.BUY,
                "123 Street",
                "https://img.url",
                "0821234567",
                ProfileRoles.CUSTOMER
        );
        profile.setId("customerId");
        return profile;
    }

    private Order orderWithStage(OrderStage stage) {
        Order order = new Order();
        order.setId("order123");
        order.setCustomerId("customerId");
        order.setStage(stage);
        return order;
    }

    private StoreProfile storeProfile() {
        return mock(StoreProfile.class);
    }

    private StoreProfile moversStoreProfile() {
        StoreProfile store = mock(StoreProfile.class);
        when(store.getStoreType()).thenReturn(StoreType.MOVERS);
        return store;
    }

    private OrderUpdatedEvent event(Order order) {
        return new OrderUpdatedEvent(this, order, "messengerId", storeProfile());
    }

    private OrderUpdatedEvent event(Order order, StoreProfile store) {
        return new OrderUpdatedEvent(this, order, "messengerId", store);
    }

    // ─── STAGE_5_ARRIVED ─────────────────────────────────────────────────────

    @Test
    void handleOrderUpdatedEvent_stage5Arrived_sendsDropOffTemplateAndPushNotification() throws Exception {
        Order order = orderWithStage(OrderStage.STAGE_5_ARRIVED);
        UserProfile customer = customer();
        List<Device> devices = List.of(new Device("token123"));

        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(devices);

        handler.handleOrderUpdatedEvent(event(order));

        verify(pushNotificationService).sendNotifications(eq(devices), any(PushMessage.class));
        verify(whatsappNotificationService).notifyDriverArrivedForDropOff(order, customer);
        verify(whatsappNotificationService, never()).notifyDriverArrivedForPickup(any(), any());
        verify(whatsappNotificationService, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void handleOrderUpdatedEvent_stage5Arrived_noDevices_skipsPushButStillSendsWhatsApp() throws Exception {
        Order order = orderWithStage(OrderStage.STAGE_5_ARRIVED);
        UserProfile customer = customer();

        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(Collections.emptyList());

        handler.handleOrderUpdatedEvent(event(order));

        verify(pushNotificationService, never()).sendNotifications(any(), any());
        verify(whatsappNotificationService).notifyDriverArrivedForDropOff(order, customer);
    }

    // ─── STAGE_6_WITH_CUSTOMER ───────────────────────────────────────────────

    @Test
    void handleOrderUpdatedEvent_stage6WithCustomer_sendsPushAndAdvancesStage() throws Exception {
        Order order = orderWithStage(OrderStage.STAGE_6_WITH_CUSTOMER);
        UserProfile customer = customer();
        List<Device> devices = List.of(new Device("token456"));

        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(devices);

        handler.handleOrderUpdatedEvent(event(order));

        verify(pushNotificationService).sendNotifications(eq(devices), any(PushMessage.class));
        verify(whatsappNotificationService, never()).notifyDriverArrivedForPickup(any(), any());
        verify(whatsappNotificationService, never()).notifyDriverArrivedForDropOff(any(), any());
        verify(whatsappNotificationService, never()).sendMessage(anyString(), anyString());
        assertEquals(OrderStage.STAGE_7_ALL_PAID, order.getStage());
    }

    // ─── STAGE_2_STORE_PROCESSING ────────────────────────────────────────────

    @Test
    void handleOrderUpdatedEvent_stage2Processing_sendsPushOnly() throws Exception {
        Order order = orderWithStage(OrderStage.STAGE_2_STORE_PROCESSING);
        UserProfile customer = customer();
        List<Device> devices = List.of(new Device("token789"));

        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(devices);

        handler.handleOrderUpdatedEvent(event(order));

        verify(pushNotificationService).sendNotifications(eq(devices), any(PushMessage.class));
        verify(whatsappNotificationService, never()).notifyDriverArrivedForPickup(any(), any());
        verify(whatsappNotificationService, never()).notifyDriverArrivedForDropOff(any(), any());
        verify(whatsappNotificationService, never()).sendMessage(anyString(), anyString());
    }

    // ─── STAGE_4_ON_THE_ROAD ─────────────────────────────────────────────────

    @Test
    void handleOrderUpdatedEvent_stage4OnTheRoad_sendsPushOnly() throws Exception {
        Order order = orderWithStage(OrderStage.STAGE_4_ON_THE_ROAD);
        UserProfile customer = customer();
        List<Device> devices = List.of(new Device("token101"));

        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(devices);

        handler.handleOrderUpdatedEvent(event(order));

        verify(pushNotificationService).sendNotifications(eq(devices), any(PushMessage.class));
        verify(whatsappNotificationService, never()).notifyDriverArrivedForPickup(any(), any());
        verify(whatsappNotificationService, never()).notifyDriverArrivedForDropOff(any(), any());
        verify(whatsappNotificationService, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void handleOrderUpdatedEvent_stage3ReadyForCollection_nonMoversWithDevices_doesNotSendPushOrWhatsapp() {
        Order order = orderWithStage(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        UserProfile customer = customer();
        List<Device> devices = List.of(new Device("token102"));

        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(devices);

        handler.handleOrderUpdatedEvent(event(order));

        verify(pushNotificationService, never()).sendNotifications(any(), any());
        verifyNoInteractions(whatsappNotificationService);
    }

    // ─── Early-return stages ─────────────────────────────────────────────────

    @Test
    void handleOrderUpdatedEvent_stage0NotPaid_returnsEarlyWithoutAnyNotification() {
        Order order = orderWithStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        UserProfile customer = customer();
        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(Collections.emptyList());

        handler.handleOrderUpdatedEvent(event(order));

        verifyNoInteractions(pushNotificationService, whatsappNotificationService);
    }

    @Test
    void handleOrderUpdatedEvent_stage1WaitingConfirm_returnsEarlyWithoutAnyNotification() {
        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        UserProfile customer = customer();
        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(Collections.emptyList());

        handler.handleOrderUpdatedEvent(event(order));

        verifyNoInteractions(pushNotificationService, whatsappNotificationService);
    }

    // ─── WhatsApp failure resilience ─────────────────────────────────────────

    @Test
    void handleOrderUpdatedEvent_whatsAppFailureForPickup_doesNotBreakFlow() throws Exception {
        Order order = orderWithStage(OrderStage.STAGE_3_READY_FOR_COLLECTION);
        UserProfile customer = customer();

        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(Collections.emptyList());
        doThrow(new IOException("WhatsApp service unavailable"))
                .when(whatsappNotificationService).notifyDriverArrivedForPickup(any(), any());

        handler.handleOrderUpdatedEvent(event(order, moversStoreProfile()));

        verify(pushNotificationService, never()).sendNotifications(any(), any());
    }

    @Test
    void handleOrderUpdatedEvent_whatsAppFailureForDropOff_doesNotSuppressPushNotification() throws Exception {
        Order order = orderWithStage(OrderStage.STAGE_5_ARRIVED);
        UserProfile customer = customer();
        List<Device> devices = List.of(new Device("tokenErr2"));

        when(userProfileService.find("customerId")).thenReturn(customer);
        when(deviceService.findByUserId("customerId")).thenReturn(devices);
        doThrow(new IOException("WhatsApp service unavailable"))
                .when(whatsappNotificationService).notifyDriverArrivedForDropOff(any(), any());

        handler.handleOrderUpdatedEvent(event(order));

        verify(pushNotificationService).sendNotifications(eq(devices), any(PushMessage.class));
    }
}
