package io.curiousoft.izinga.messaging.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateRequest;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappNotificationServiceTest {

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private WhatsappConfig whatsappConfig;

    @SuppressWarnings("unchecked")
    private final Call<WhatsappTemplateResponse> callMock = mock(Call.class);

    private WhatsappNotificationService service;

    @BeforeEach
    void setUp() {
        when(whatsappConfig.phoneId()).thenReturn("testPhoneId");
        service = new WhatsappNotificationService(whatsAppService, whatsappConfig, new ObjectMapper(), "https://video.url");
    }

    private UserProfile customerProfile(String name, String mobile) {
        return new UserProfile(name, UserProfile.SignUpReason.BUY, "Address", "img", mobile, ProfileRoles.CUSTOMER);
    }

    private Order orderWithId(String id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }

    // ─── notifyDriverArrivedForPickup ────────────────────────────────────────

    @Test
    void notifyDriverArrivedForPickup_sendsPickupTemplateWithCorrectRecipientAndParams() throws IOException {
        Order order = orderWithId("order123");
        UserProfile customer = customerProfile("John Doe", "0821234567");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForPickup(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(eq("testPhoneId"), captor.capture());
        verify(callMock).execute();

        WhatsappTemplateRequest request = captor.getValue();
        assertEquals("+27821234567", request.getTo());
        assertEquals("driver_arrived_pickup", request.getTemplate().getName());
        var bodyComp = request.getTemplate().getComponents().get(0);
        assertEquals(WhatsappTemplateRequest.ComponentType.BODY, bodyComp.getType());
        assertEquals("John Doe", bodyComp.getParameters().get(0).getText());
        assertEquals("order123", bodyComp.getParameters().get(1).getText());
    }

    @Test
    void notifyDriverArrivedForPickup_normalizesZeroPrefixMobileNumber() throws IOException {
        Order order = orderWithId("order123");
        UserProfile customer = customerProfile("Jane", "0839991234");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForPickup(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        assertEquals("+27839991234", captor.getValue().getTo());
    }

    @Test
    void notifyDriverArrivedForPickup_usesFallbackNameWhenCustomerNameIsNull() throws IOException {
        Order order = orderWithId("order123");
        UserProfile customer = customerProfile(null, "+27821234567");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForPickup(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("Customer", bodyComp.getParameters().get(0).getText());
    }

    @Test
    void notifyDriverArrivedForPickup_handlesCustomerNameWithJsonSpecialCharacters() throws IOException {
        Order order = orderWithId("order123");
        UserProfile customer = customerProfile("John \"Danger\" O'Brien\\Jr", "0821234567");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        // Must not throw due to JSON injection
        assertDoesNotThrow(() -> service.notifyDriverArrivedForPickup(order, customer));
        verify(callMock).execute();

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("John \"Danger\" O'Brien\\Jr", bodyComp.getParameters().get(0).getText());
    }

    // ─── notifyDriverArrivedForDropOff ───────────────────────────────────────

    @Test
    void notifyDriverArrivedForDropOff_sendsDropOffTemplateWithCorrectRecipientAndParams() throws IOException {
        Order order = orderWithId("order456");
        UserProfile customer = customerProfile("Jane Doe", "0829876543");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForDropOff(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(eq("testPhoneId"), captor.capture());
        verify(callMock).execute();

        WhatsappTemplateRequest request = captor.getValue();
        assertEquals("+27829876543", request.getTo());
        assertEquals("driver_arrived_dropoff", request.getTemplate().getName());
        var bodyComp = request.getTemplate().getComponents().get(0);
        assertEquals(WhatsappTemplateRequest.ComponentType.BODY, bodyComp.getType());
        assertEquals("Jane Doe", bodyComp.getParameters().get(0).getText());
        assertEquals("order456", bodyComp.getParameters().get(1).getText());
    }

    @Test
    void notifyDriverArrivedForDropOff_normalizesZeroPrefixMobileNumber() throws IOException {
        Order order = orderWithId("order456");
        UserProfile customer = customerProfile("Jane", "0829876543");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForDropOff(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        assertEquals("+27829876543", captor.getValue().getTo());
    }

    @Test
    void notifyDriverArrivedForDropOff_usesFallbackNameWhenCustomerNameIsNull() throws IOException {
        Order order = orderWithId("order456");
        UserProfile customer = customerProfile(null, "+27829876543");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForDropOff(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("Customer", bodyComp.getParameters().get(0).getText());
    }

    @Test
    void notifyDriverArrivedForDropOff_handlesCustomerNameWithJsonSpecialCharacters() throws IOException {
        Order order = orderWithId("order456");
        UserProfile customer = customerProfile("Jane \"The\" Smith\\Doe", "0829876543");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        assertDoesNotThrow(() -> service.notifyDriverArrivedForDropOff(order, customer));
        verify(callMock).execute();

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("Jane \"The\" Smith\\Doe", bodyComp.getParameters().get(0).getText());
    }
}
