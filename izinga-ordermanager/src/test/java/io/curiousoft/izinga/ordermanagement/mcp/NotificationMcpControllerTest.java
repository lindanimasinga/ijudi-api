package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ADR-029: Unit tests for {@link NotificationMcpController}.
 *
 * <p>Covers the two MCP notification tools with 100% branch coverage:
 * <ul>
 *   <li>{@code send_landing_options_notification} — null-audience DENY path and
 *       authenticated ALLOW path.</li>
 *   <li>{@code send_shop_options_notification} — public (no gate), null-audience
 *       ALLOW and authenticated ALLOW paths.</li>
 * </ul>
 * </p>
 *
 * <p>ThreadLocal context is set directly (same approach as
 * {@link McpAccessMatrixTest#setMcpContext}) and cleared unconditionally in
 * {@link #clearContext()}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationMcpControllerTest {

    @Mock private WhatsappNotificationService whatsappNotificationService;
    @Mock private McpCaller mcpCaller;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    @InjectMocks
    private NotificationMcpController controller;

    @AfterEach
    void clearContext() {
        StoreScopeContext.clear();
        CallerIdentityContext.clear();
    }

    // ==================================================================================
    // send_landing_options_notification — Tool 1 (authenticated, gated)
    // ==================================================================================

    /**
     * Null-audience gate: SEC-MCP-01 path.
     * When no scope token is present, the gate fires, records a violation audit event,
     * and throws CallerIdentityViolationException. The WhatsApp service must NOT be called.
     */
    @Test
    void sendLandingOptionsNotification_nullAudience_firesGateAndThrows() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(null); // propagates null to CallerIdentityContext.getAudience()

        assertThatThrownBy(() -> controller.sendLandingOptionsNotification("+27821234567", "Test User"))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessage(NotificationMcpController.UNAUTHENTICATED_MESSAGE);

        // Audit must be recorded before the throw
        verify(agentIdentityAuditService).recordViolation(
                isNull(),                                           // callerPhone=null (no token)
                eq("+27821234567"),                                 // resourceId=mobileNumber
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("send_landing_options_notification"),
                eq("UNKNOWN"),
                isNull(),
                isNull());

        // WhatsApp service must never be reached when the gate fires
        verify(whatsappNotificationService, never())
                .sendLandingOptions(any(), any(), any(), any(), any());
    }

    /**
     * DRIVER audience: gate does not fire (CallerIdentityContext.getAudience() = "DRIVER").
     * The notification service is called with the caller's typed audience and store scope.
     */
    @Test
    void sendLandingOptionsNotification_driverAudience_delegatesAndReturnsSuccessString() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.DRIVER); // propagates "DRIVER" to CallerIdentityContext
        when(mcpCaller.getAudience()).thenReturn(Audience.DRIVER);
        when(mcpCaller.getPermittedStoreId()).thenReturn(null);

        String result = controller.sendLandingOptionsNotification("+27821234567", "Test User");

        assertThat(result).isEqualTo("Landing options notification sent to +27821234567.");
        verify(whatsappNotificationService)
                .sendLandingOptions("+27821234567", "Test User", null, Audience.DRIVER, null);
        verify(agentIdentityAuditService, never())
                .recordViolation(any(), any(), any(), anyString(), anyString(), any(), any());
    }

    /**
     * STORE audience with a permitted storeId: audience and storeId are forwarded to the
     * notification service so the correct branded WhatsApp line is selected (REQ-23/REQ-24).
     */
    @Test
    void sendLandingOptionsNotification_storeAudienceWithStoreId_forwardsStoreIdToService() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setPermittedStoreId("store-id-99");
        when(mcpCaller.getAudience()).thenReturn(Audience.STORE);
        when(mcpCaller.getPermittedStoreId()).thenReturn("store-id-99");

        String result = controller.sendLandingOptionsNotification("+27821234567", "Test User");

        assertThat(result).isEqualTo("Landing options notification sent to +27821234567.");
        verify(whatsappNotificationService)
                .sendLandingOptions("+27821234567", "Test User", null, Audience.STORE, "store-id-99");
    }

    /**
     * CUSTOMER audience: gate does not fire; service receives CUSTOMER audience and null storeId.
     */
    @Test
    void sendLandingOptionsNotification_customerAudience_delegatesAndReturnsSuccessString() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        when(mcpCaller.getAudience()).thenReturn(Audience.CUSTOMER);
        when(mcpCaller.getPermittedStoreId()).thenReturn(null);

        String result = controller.sendLandingOptionsNotification("+27821234567", "Test User");

        assertThat(result).isEqualTo("Landing options notification sent to +27821234567.");
        verify(whatsappNotificationService)
                .sendLandingOptions("+27821234567", "Test User", null, Audience.CUSTOMER, null);
    }

    /**
     * ADMIN audience: gate does not fire (ADMIN audience is non-null).
     */
    @Test
    void sendLandingOptionsNotification_adminAudience_delegatesAndReturnsSuccessString() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);
        when(mcpCaller.getAudience()).thenReturn(Audience.ADMIN);
        when(mcpCaller.getPermittedStoreId()).thenReturn(null);

        String result = controller.sendLandingOptionsNotification("+27821234567", "Test User");

        assertThat(result).isEqualTo("Landing options notification sent to +27821234567.");
        verify(whatsappNotificationService)
                .sendLandingOptions("+27821234567", "Test User", null, Audience.ADMIN, null);
    }

    /**
     * OTP audience: gate does not fire (OTP is non-null).
     */
    @Test
    void sendLandingOptionsNotification_otpAudience_delegatesAndReturnsSuccessString() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.OTP);
        when(mcpCaller.getAudience()).thenReturn(Audience.OTP);
        when(mcpCaller.getPermittedStoreId()).thenReturn(null);

        String result = controller.sendLandingOptionsNotification("+27821234567", "Test User");

        assertThat(result).isEqualTo("Landing options notification sent to +27821234567.");
        verify(whatsappNotificationService)
                .sendLandingOptions("+27821234567", "Test User", null, Audience.OTP, null);
    }

    // ==================================================================================
    // send_shop_options_notification — Tool 2 (@McpPublicTool, no gate)
    // ==================================================================================

    /**
     * Public tool, null audience, service reports success: no gate fires. The shop options
     * notification is sent regardless of token presence. Controller returns the success string.
     * The audit service must NOT be called.
     */
    @Test
    void sendShopOptionsNotification_nullAudience_sendsNotificationWithoutGate() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(null); // tokenless — no scope token
        when(whatsappNotificationService.sendShopOptions("+27821234567", "Test User")).thenReturn(true);

        String result = controller.sendShopOptionsNotification("+27821234567", "Test User");

        assertThat(result).isEqualTo("Shop options notification sent to +27821234567.");
        verify(whatsappNotificationService).sendShopOptions("+27821234567", "Test User");
        verify(agentIdentityAuditService, never())
                .recordViolation(any(), any(), any(), anyString(), anyString(), any(), any());
    }

    /**
     * Public tool, authenticated DRIVER, service reports success: tool passes through, success returned.
     */
    @Test
    void sendShopOptionsNotification_driverAudience_sendsNotificationAndReturnsSuccessString() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.DRIVER);
        when(whatsappNotificationService.sendShopOptions("+27821234567", "Test User")).thenReturn(true);

        String result = controller.sendShopOptionsNotification("+27821234567", "Test User");

        assertThat(result).isEqualTo("Shop options notification sent to +27821234567.");
        verify(whatsappNotificationService).sendShopOptions("+27821234567", "Test User");
    }

    /**
     * Public tool, authenticated CUSTOMER, service reports success: tool passes through, success returned.
     */
    @Test
    void sendShopOptionsNotification_customerAudience_sendsNotificationAndReturnsSuccessString() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        when(whatsappNotificationService.sendShopOptions("+27821234567", "Test User")).thenReturn(true);

        String result = controller.sendShopOptionsNotification("+27821234567", "Test User");

        assertThat(result).isEqualTo("Shop options notification sent to +27821234567.");
        verify(whatsappNotificationService).sendShopOptions("+27821234567", "Test User");
    }

    /**
     * FIX-2 (AC-09 truthfulness): When sendShopOptions returns false (absorbed failure —
     * e.g. unapproved Meta template, MongoDB timeout during sender resolution), the controller
     * must NOT return the success string. It must return a clearly distinct degraded-outcome
     * message so the agent accurately reports to the user that the notification was not delivered.
     */
    @Test
    void sendShopOptionsNotification_serviceReturnsFalse_returnsDegradedOutcomeString() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(null); // tokenless — public tool still executes
        when(whatsappNotificationService.sendShopOptions("+27821234567", "Test User")).thenReturn(false);

        String result = controller.sendShopOptionsNotification("+27821234567", "Test User");

        assertThat(result)
                .as("Controller must return a degraded-outcome message, not the success string, " +
                    "when sendShopOptions reports a failed delivery (AC-09 truthfulness fix)")
                .isEqualTo("Shop options notification could not be delivered to +27821234567 at this time.");
        verify(whatsappNotificationService).sendShopOptions("+27821234567", "Test User");
        verify(agentIdentityAuditService, never())
                .recordViolation(any(), any(), any(), anyString(), anyString(), any(), any());
    }
}
