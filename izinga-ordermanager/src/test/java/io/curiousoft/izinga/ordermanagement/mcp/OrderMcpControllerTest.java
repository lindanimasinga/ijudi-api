package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.ShippingData;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ADR-026 Phase 3: Unit tests for OrderMcpController gate enforcement.
 *
 * <p>These tests prove that the gate logic (null-audience rejection, ownership
 * enforcement, AMD-01 audience routing) now lives in the controller, NOT in
 * OrderServiceImpl. Each test sets up the CallerIdentityContext /
 * StoreScopeContext and verifies that the controller allows or rejects the call
 * correctly, and that it delegates correctly to the service when gates pass.
 *
 * <p>Coverage:
 * - find_order_by_id: null-audience reject + audit; CUSTOMER/DRIVER ownership
 *   gate; STORE/ADMIN/OTP pass-through; non-MCP bypass.
 * - find_orders_by_user_id: validateCallerOwnsId gate; ADMIN pass-through.
 * - find_order_for_messenger: null-audience reject + audit; DRIVER ownership
 *   gate; CUSTOMER ownership gate; STORE/ADMIN pass-through.
 * - find_orders_by_phone_number: validateCallerPhone gate; ADMIN pass-through.
 * - find_orders_by_messenger_id: AMD-01 null/CUSTOMER/STORE reject + audit;
 *   DRIVER ownership gate; ADMIN pass-through; non-MCP bypass.
 */
@ExtendWith(MockitoExtension.class)
public class OrderMcpControllerTest {

    @Mock private OrderServiceImpl orderService;
    @Mock private McpCaller mcpCaller;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    private OrderMcpController sut;

    private static final String CALLER_PHONE  = "+27812345678";
    private static final String CALLER_USER_ID = "caller-user-id";
    private static final String OTHER_USER_ID  = "other-user-id";
    private static final String ORDER_ID       = "order-1";

    @BeforeEach
    public void setUp() {
        sut = new OrderMcpController(orderService, mcpCaller, agentIdentityAuditService);
        CallerIdentityContext.clear();
        StoreScopeContext.clear();
    }

    @AfterEach
    public void tearDown() {
        CallerIdentityContext.clear();
        StoreScopeContext.clear();
    }

    // ========== find_order_by_id ==========================================================

    @Test
    public void findOrder_mcpNullAudience_throwsAndAudits() {
        // SEC-MCP-01 / SEC-MCP-BUG-01: tokenless request hard-rejected BEFORE repo access
        CallerIdentityContext.setMcpRequest(true);
        // No audience set — CallerIdentityContext.getAudience() == null

        CallerIdentityViolationException ex = assertThrows(
                CallerIdentityViolationException.class,
                () -> sut.findOrder(ORDER_ID));

        assertTrue(ex.getMessage().startsWith("IDENTITY_REFUSAL"),
                "Rejection message must start with IDENTITY_REFUSAL");
        verify(agentIdentityAuditService).recordViolation(
                isNull(), eq(ORDER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_order_by_id"), eq("UNKNOWN"), isNull(), isNull());
        // Gate fires BEFORE service call — service must never be touched
        verifyNoInteractions(orderService);
    }

    @Test
    public void findOrder_mcpNullAudience_rejectionMessageIsIdentityRefusal() {
        CallerIdentityContext.setMcpRequest(true);

        CallerIdentityViolationException ex = assertThrows(
                CallerIdentityViolationException.class,
                () -> sut.findOrder(ORDER_ID));

        assertEquals(OrderMcpController.UNAUTHENTICATED_MESSAGE, ex.getMessage());
    }

    @Test
    public void findOrder_mcpCustomerAudience_ownOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        Order order = orderWithCustomer(CALLER_USER_ID);
        when(orderService.findOrder(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrder(ORDER_ID);
        assertSame(order, result);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findOrder_mcpCustomerAudience_thirdPartyOrder_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        Order order = orderWithCustomer(OTHER_USER_ID);
        when(orderService.findOrder(ORDER_ID)).thenReturn(order);

        assertThrows(CallerIdentityViolationException.class, () -> sut.findOrder(ORDER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(ORDER_ID),
                eq(AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED),
                eq("find_order_by_id"), eq("CUSTOMER"), isNull(), isNull());
    }

    @Test
    public void findOrder_mcpDriverAudience_assignedOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.DRIVER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        Order order = orderWithMessenger(CALLER_USER_ID);
        when(orderService.findOrder(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrder(ORDER_ID);
        assertSame(order, result);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findOrder_mcpDriverAudience_notAssignedOrder_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.DRIVER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        Order order = orderWithMessenger(OTHER_USER_ID);
        when(orderService.findOrder(ORDER_ID)).thenReturn(order);

        assertThrows(CallerIdentityViolationException.class, () -> sut.findOrder(ORDER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(ORDER_ID),
                eq(AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED),
                eq("find_order_by_id"), eq("DRIVER"), isNull(), isNull());
    }

    @Test
    public void findOrder_mcpAdminAudience_anyOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.ADMIN.name());

        Order order = orderWithCustomer(OTHER_USER_ID);
        when(orderService.findOrder(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrder(ORDER_ID);
        assertSame(order, result);
        // ADMIN is not CUSTOMER or DRIVER — no ownership check, no audit
        verifyNoInteractions(agentIdentityAuditService);
        verify(mcpCaller, never()).resolveUserId();
    }

    @Test
    public void findOrder_mcpStoreAudience_anyOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.STORE.name());

        Order order = orderWithCustomer(OTHER_USER_ID);
        when(orderService.findOrder(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrder(ORDER_ID);
        assertSame(order, result);
        verifyNoInteractions(agentIdentityAuditService);
        verify(mcpCaller, never()).resolveUserId();
    }

    @Test
    public void findOrder_nonMcp_noGate_passesThrough() {
        // Non-MCP: CallerIdentityContext.isMcpRequest() == false → gate is skipped entirely
        Order order = orderWithCustomer(OTHER_USER_ID);
        when(orderService.findOrder(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrder(ORDER_ID);
        assertSame(order, result);
        verifyNoInteractions(agentIdentityAuditService, mcpCaller);
    }

    // ========== find_orders_by_user_id ====================================================

    @Test
    public void findOrderByUserId_mcpCustomerAudience_ownId_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);
        when(orderService.findOrderByUserId(CALLER_USER_ID)).thenReturn(List.of());

        assertDoesNotThrow(() -> sut.findOrderByUserId(CALLER_USER_ID));
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findOrderByUserId_mcpCustomerAudience_thirdPartyId_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findOrderByUserId(OTHER_USER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OTHER_USER_ID),
                eq(AgentIdentityAuditLog.ViolationType.ID_NOT_OWNED),
                eq("find_orders_by_user_id"), eq("CUSTOMER"), isNull(), isNull());
        verify(orderService, never()).findOrderByUserId(any());
    }

    @Test
    public void findOrderByUserId_mcpAdminAudience_anyId_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.ADMIN.name());
        when(orderService.findOrderByUserId(OTHER_USER_ID)).thenReturn(List.of());

        assertDoesNotThrow(() -> sut.findOrderByUserId(OTHER_USER_ID));
        // ADMIN: StoreScopeValidator.validateCallerOwnsId skips the ownership check
        verifyNoInteractions(agentIdentityAuditService);
        verify(mcpCaller, never()).resolveUserId();
    }

    // ========== find_order_for_messenger ==================================================

    @Test
    public void findOrderForMessenger_mcpNullAudience_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        // No audience

        CallerIdentityViolationException ex = assertThrows(
                CallerIdentityViolationException.class,
                () -> sut.findOrderForMessenger(ORDER_ID));

        assertEquals(OrderMcpController.UNAUTHENTICATED_MESSAGE, ex.getMessage());
        verify(agentIdentityAuditService).recordViolation(
                isNull(), eq(ORDER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_order_for_messenger"), eq("UNKNOWN"), isNull(), isNull());
        verifyNoInteractions(orderService);
    }

    @Test
    public void findOrderForMessenger_mcpDriverAudience_assignedOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.DRIVER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        Order order = orderWithMessenger(CALLER_USER_ID);
        when(orderService.findOrderForMessenger(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrderForMessenger(ORDER_ID);
        assertSame(order, result);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findOrderForMessenger_mcpDriverAudience_notAssigned_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.DRIVER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        Order order = orderWithMessenger(OTHER_USER_ID);
        when(orderService.findOrderForMessenger(ORDER_ID)).thenReturn(order);

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findOrderForMessenger(ORDER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(ORDER_ID),
                eq(AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED),
                eq("find_order_for_messenger"), eq("DRIVER"), isNull(), isNull());
    }

    @Test
    public void findOrderForMessenger_mcpCustomerAudience_ownOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        Order order = orderWithCustomer(CALLER_USER_ID);
        when(orderService.findOrderForMessenger(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrderForMessenger(ORDER_ID);
        assertSame(order, result);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findOrderForMessenger_mcpCustomerAudience_thirdPartyOrder_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        Order order = orderWithCustomer(OTHER_USER_ID);
        when(orderService.findOrderForMessenger(ORDER_ID)).thenReturn(order);

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findOrderForMessenger(ORDER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(ORDER_ID),
                eq(AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED),
                eq("find_order_for_messenger"), eq("CUSTOMER"), isNull(), isNull());
    }

    @Test
    public void findOrderForMessenger_mcpStoreAudience_anyOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.STORE.name());

        Order order = orderWithCustomer(OTHER_USER_ID);
        when(orderService.findOrderForMessenger(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrderForMessenger(ORDER_ID);
        assertSame(order, result);
        // STORE is neither DRIVER nor CUSTOMER — no ownership check
        verifyNoInteractions(agentIdentityAuditService);
        verify(mcpCaller, never()).resolveUserId();
    }

    @Test
    public void findOrderForMessenger_nonMcp_noGate() {
        Order order = orderWithCustomer(OTHER_USER_ID);
        when(orderService.findOrderForMessenger(ORDER_ID)).thenReturn(order);

        Order result = sut.findOrderForMessenger(ORDER_ID);
        assertSame(order, result);
        verifyNoInteractions(agentIdentityAuditService, mcpCaller);
    }

    // ========== find_orders_by_phone_number ===============================================

    @Test
    public void findOrderByPhone_mcpCustomerAudience_ownPhone_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(orderService.findOrderByPhone(CALLER_PHONE)).thenReturn(List.of());

        assertDoesNotThrow(() -> sut.findOrderByPhone(CALLER_PHONE));
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findOrderByPhone_mcpCustomerAudience_thirdPartyPhone_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        String otherPhone = "+27800000000";
        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findOrderByPhone(otherPhone));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(otherPhone),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("find_orders_by_phone_number"), eq("CUSTOMER"), isNull(), isNull());
        verify(orderService, never()).findOrderByPhone(any());
    }

    @Test
    public void findOrderByPhone_mcpAdminAudience_anyPhone_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.ADMIN.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        String otherPhone = "+27800000000";
        when(orderService.findOrderByPhone(otherPhone)).thenReturn(List.of());

        assertDoesNotThrow(() -> sut.findOrderByPhone(otherPhone));
        // ADMIN early return in validateCallerPhone → no audit
        verifyNoInteractions(agentIdentityAuditService);
    }

    // ========== find_orders_by_messenger_id ===============================================

    @Test
    public void findOrderByMessengerId_mcpNullAudience_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        // No audience

        CallerIdentityViolationException ex = assertThrows(
                CallerIdentityViolationException.class,
                () -> sut.findOrderByMessengerId(CALLER_USER_ID, false));

        assertEquals(OrderMcpController.UNAUTHENTICATED_MESSAGE, ex.getMessage());
        verify(agentIdentityAuditService).recordViolation(
                isNull(), eq(CALLER_USER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_orders_by_messenger_id"), eq("UNKNOWN"), isNull(), isNull());
        verifyNoInteractions(orderService);
    }

    @Test
    public void findOrderByMessengerId_mcpCustomerAudience_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        CallerIdentityViolationException ex = assertThrows(
                CallerIdentityViolationException.class,
                () -> sut.findOrderByMessengerId(CALLER_USER_ID, false));

        assertEquals(OrderMcpController.VIOLATION_MESSAGE, ex.getMessage());
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(CALLER_USER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_orders_by_messenger_id"), eq("CUSTOMER"), isNull(), isNull());
        verifyNoInteractions(orderService);
    }

    @Test
    public void findOrderByMessengerId_mcpStoreAudience_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.STORE.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findOrderByMessengerId(CALLER_USER_ID, false));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(CALLER_USER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_orders_by_messenger_id"), eq("STORE"), isNull(), isNull());
    }

    @Test
    public void findOrderByMessengerId_mcpDriverAudience_ownId_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.DRIVER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);
        when(orderService.findOrderByMessengerId(CALLER_USER_ID, false)).thenReturn(Set.of());

        assertDoesNotThrow(() -> sut.findOrderByMessengerId(CALLER_USER_ID, false));
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findOrderByMessengerId_mcpDriverAudience_otherUserId_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.DRIVER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_USER_ID);

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findOrderByMessengerId(OTHER_USER_ID, false));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OTHER_USER_ID),
                eq(AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED),
                eq("find_orders_by_messenger_id"), eq("DRIVER"), isNull(), isNull());
        verify(orderService, never()).findOrderByMessengerId(any(), anyBoolean());
    }

    @Test
    public void findOrderByMessengerId_mcpAdminAudience_anyId_passes() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.ADMIN.name());
        when(orderService.findOrderByMessengerId(OTHER_USER_ID, false)).thenReturn(Set.of());

        assertDoesNotThrow(() -> sut.findOrderByMessengerId(OTHER_USER_ID, false));
        verifyNoInteractions(agentIdentityAuditService);
        verify(mcpCaller, never()).resolveUserId();
    }

    @Test
    public void findOrderByMessengerId_nonMcp_noGate() {
        // CallerIdentityContext.isMcpRequest() == false → entire gate block skipped
        when(orderService.findOrderByMessengerId(OTHER_USER_ID, true)).thenReturn(Set.of());

        assertDoesNotThrow(() -> sut.findOrderByMessengerId(OTHER_USER_ID, true));
        verifyNoInteractions(agentIdentityAuditService, mcpCaller);
    }

    // ========== Helpers ==================================================================

    private Order orderWithCustomer(String customerId) {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setCustomerId(customerId);
        return o;
    }

    private Order orderWithMessenger(String messengerId) {
        Order o = new Order();
        o.setId(ORDER_ID);
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(messengerId);
        o.setShippingData(sd);
        return o;
    }
}
