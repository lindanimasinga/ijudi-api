package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.ordermanagement.orders.McpOrderService;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderCreationMcpController} with 100% branch coverage.
 *
 * <p>Covers all 5 gate steps plus the delegate-to-service happy path.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderCreationMcpControllerTest {

    private static final String CUSTOMER_ID    = "cust-123";
    private static final String CALLER_PHONE   = "+27801112222";
    private static final String IDEMPOTENCY_KEY = "idem-key-1";
    private static final String SHOP_ID        = "shop-1";
    private static final String BASKET_JSON    = "{\"items\":[]}";

    @Mock private McpOrderService          mcpOrderService;
    @Mock private UserProfileRepo          userProfileRepo;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    private McpCaller                  mcpCaller;
    private OrderCreationMcpController controller;

    @BeforeEach
    void setUp() {
        mcpCaller  = new McpCaller(userProfileRepo);
        controller = new OrderCreationMcpController(mcpOrderService, mcpCaller, agentIdentityAuditService);
    }

    @AfterEach
    void tearDown() {
        CallerIdentityContext.clear();
        StoreScopeContext.clear();
    }

    // ---- Step 0: Audience gate ---------------------------------------------------------

    @Test
    void startOrder_mcpRequest_nullAudience_returnsIdentityRefusal() {
        CallerIdentityContext.setMcpRequest(true);
        // No audience set

        McpOrderService.StartOrderResponse response = callStartOrder();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("IDENTITY_REFUSAL");
        assertThat(response.getMessage()).contains("authenticated scope token");
        verifyNoInteractions(mcpOrderService, agentIdentityAuditService);
    }

    @Test
    void startOrder_mcpRequest_driverAudience_returnsIdentityRefusal() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("DRIVER");

        McpOrderService.StartOrderResponse response = callStartOrder();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("IDENTITY_REFUSAL");
        verifyNoInteractions(mcpOrderService, agentIdentityAuditService);
    }

    @Test
    void startOrder_mcpRequest_otpAudience_returnsIdentityRefusal() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("OTP");

        McpOrderService.StartOrderResponse response = callStartOrder();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("IDENTITY_REFUSAL");
        verifyNoInteractions(mcpOrderService, agentIdentityAuditService);
    }

    // ---- Step 0 → non-MCP path: audience gate is skipped entirely --------------------

    @Test
    void startOrder_notMcpRequest_audienceGateSkipped_proceedsToCustomerExistenceCheck() {
        // isMcpRequest() = false → Step 0 is skipped; next blocking gate is Step 3
        CallerIdentityContext.setMcpRequest(false);
        when(mcpOrderService.customerExists(CUSTOMER_ID)).thenReturn(false);

        McpOrderService.StartOrderResponse response = callStartOrder();

        // Fails at Step 3 (customer not found), proving Step 0 was skipped
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("customer");
    }

    // ---- Step 1: Consent gate ----------------------------------------------------------

    @Test
    void startOrder_customerAudience_consentFalse_returnsConsentRejected() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");

        McpOrderService.StartOrderResponse response = controller.startOrder(
                CUSTOMER_ID, false, IDEMPOTENCY_KEY, SHOP_ID, "desc", "ONLINE", BASKET_JSON, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("confirmation");
        verifyNoInteractions(mcpOrderService, agentIdentityAuditService);
    }

    // ---- Step 2: Idempotency key -------------------------------------------------------

    @Test
    void startOrder_blankIdempotencyKey_returnsIdempotencyRejected() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");

        McpOrderService.StartOrderResponse response = controller.startOrder(
                CUSTOMER_ID, true, "   ", SHOP_ID, "desc", "ONLINE", BASKET_JSON, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("idempotency");
        verifyNoInteractions(mcpOrderService, agentIdentityAuditService);
    }

    @Test
    void startOrder_nullIdempotencyKey_returnsIdempotencyRejected() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");

        McpOrderService.StartOrderResponse response = controller.startOrder(
                CUSTOMER_ID, true, null, SHOP_ID, "desc", "ONLINE", BASKET_JSON, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("idempotency");
        verifyNoInteractions(mcpOrderService, agentIdentityAuditService);
    }

    // ---- Step 3: Customer existence ----------------------------------------------------

    @Test
    void startOrder_customerNotFound_returnsCustomerNotFoundRejection() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        when(mcpOrderService.customerExists(CUSTOMER_ID)).thenReturn(false);

        McpOrderService.StartOrderResponse response = callStartOrder();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("customer");
        verifyNoInteractions(agentIdentityAuditService);
        verify(mcpOrderService, never()).startOrder(any(), anyBoolean(), any(), any(), any(), any(), any(), any());
    }

    // ---- Step 4: Caller ownership ------------------------------------------------------

    @Test
    void startOrder_customerAudience_ownershipViolation_returnsIdentityRefusal() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        // Caller resolves to a DIFFERENT user id than CUSTOMER_ID
        UserProfile callerProfile = mock(UserProfile.class);
        when(callerProfile.getId()).thenReturn("caller-different-id");
        // McpCaller prefix loop: "0"+last9, "+27"+last9, "27"+last9; last9("801112222")
        when(userProfileRepo.findByMobileNumber("0801112222")).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27801112222")).thenReturn(callerProfile);
        when(mcpOrderService.customerExists(CUSTOMER_ID)).thenReturn(true);

        McpOrderService.StartOrderResponse response = callStartOrder();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("IDENTITY_REFUSAL");
        verify(mcpOrderService, never()).startOrder(any(), anyBoolean(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void startOrder_customerAudience_ownershipViolation_auditsWithCorrectParams() {
        // FIX-04: audit audience is captured from CallerIdentityContext on the request thread
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        UserProfile callerProfile = mock(UserProfile.class);
        when(callerProfile.getId()).thenReturn("caller-different-id");
        when(userProfileRepo.findByMobileNumber("0801112222")).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27801112222")).thenReturn(callerProfile);
        when(mcpOrderService.customerExists(CUSTOMER_ID)).thenReturn(true);

        callStartOrder();

        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE),
                eq(CUSTOMER_ID),
                eq(AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED),
                eq("start_order"),
                eq("CUSTOMER"),
                isNull(),
                isNull());
    }

    // ---- ADMIN audience: bypasses ownership check -------------------------------------

    @Test
    void startOrder_adminAudience_bypassesOwnershipCheck_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");
        when(mcpOrderService.customerExists(CUSTOMER_ID)).thenReturn(true);
        McpOrderService.StartOrderResponse expected = mock(McpOrderService.StartOrderResponse.class);
        when(expected.isSuccess()).thenReturn(true);
        when(mcpOrderService.startOrder(eq(CUSTOMER_ID), eq(true), eq(IDEMPOTENCY_KEY),
                eq(SHOP_ID), any(), any(), any(), any())).thenReturn(expected);

        McpOrderService.StartOrderResponse response = callStartOrder();

        assertThat(response.isSuccess()).isTrue();
        verifyNoInteractions(agentIdentityAuditService);
        // No userProfileRepo lookups — ADMIN bypasses McpCaller.resolveUserId()
        verifyNoInteractions(userProfileRepo);
    }

    // ---- STORE audience: bypasses ownership check -------------------------------------

    @Test
    void startOrder_storeAudience_bypassesOwnershipCheck_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("STORE");
        when(mcpOrderService.customerExists(CUSTOMER_ID)).thenReturn(true);
        McpOrderService.StartOrderResponse expected = mock(McpOrderService.StartOrderResponse.class);
        when(expected.isSuccess()).thenReturn(true);
        when(mcpOrderService.startOrder(eq(CUSTOMER_ID), eq(true), eq(IDEMPOTENCY_KEY),
                eq(SHOP_ID), any(), any(), any(), any())).thenReturn(expected);

        McpOrderService.StartOrderResponse response = callStartOrder();

        assertThat(response.isSuccess()).isTrue();
        verifyNoInteractions(agentIdentityAuditService);
        verifyNoInteractions(userProfileRepo);
    }

    // ---- Happy path: CUSTOMER owns the id → delegates to service --------------------

    @Test
    void startOrder_customerAudience_ownId_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        // Caller resolves to CUSTOMER_ID — caller OWNS the order being placed
        UserProfile callerProfile = mock(UserProfile.class);
        when(callerProfile.getId()).thenReturn(CUSTOMER_ID);
        when(userProfileRepo.findByMobileNumber("0801112222")).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27801112222")).thenReturn(callerProfile);
        when(mcpOrderService.customerExists(CUSTOMER_ID)).thenReturn(true);
        McpOrderService.StartOrderResponse expected = mock(McpOrderService.StartOrderResponse.class);
        when(expected.isSuccess()).thenReturn(true);
        when(mcpOrderService.startOrder(eq(CUSTOMER_ID), eq(true), eq(IDEMPOTENCY_KEY),
                eq(SHOP_ID), any(), any(), any(), any())).thenReturn(expected);

        McpOrderService.StartOrderResponse response = callStartOrder();

        assertThat(response.isSuccess()).isTrue();
        verifyNoInteractions(agentIdentityAuditService);
    }

    // ---- Helper -----------------------------------------------------------------------

    private McpOrderService.StartOrderResponse callStartOrder() {
        return controller.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", BASKET_JSON, null);
    }
}
