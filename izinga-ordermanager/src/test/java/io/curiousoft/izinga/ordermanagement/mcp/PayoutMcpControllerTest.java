package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.recon.payout.Payout;
import io.curiousoft.izinga.recon.payout.PayoutType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ADR-026 Phase 5 unit tests for {@link PayoutMcpController}.
 *
 * <p>Gate logic ported from {@code ReconServiceImplCallerIdentityTest} — these tests now live
 * at the controller layer where the identity gate runs. AC-04: the ungated
 * {@code ReconService.getAllPayouts()} is exercised only after the gate passes.</p>
 *
 * <p>Uses JUnit 5 + Mockito (consistent with izinga-ordermanager test style).</p>
 */
@ExtendWith(MockitoExtension.class)
class PayoutMcpControllerTest {

    // ---- Collaborators ------------------------------------------------------------------

    @Mock private ReconService              reconService;
    @Mock private McpCaller                 mcpCaller;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    private PayoutMcpController sut;

    // ---- Test data ----------------------------------------------------------------------

    private static final String CALLER_PHONE  = "+27812345678";
    private static final String CALLER_ID     = "caller-user-id";
    private static final String OTHER_ID      = "other-user-id";
    private static final Date   FROM          = new Date(0);
    private static final Date   TO_DATE       = new Date();

    @BeforeEach
    void setUp() {
        sut = new PayoutMcpController(reconService, mcpCaller, agentIdentityAuditService);
    }

    @AfterEach
    void tearDown() {
        CallerIdentityContext.clear();
        StoreScopeContext.clear();
    }

    // ---- Helper: set up MCP request context ----------------------------------------

    private void setMcpContext(String audience, String phone) {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(audience);
        // StoreScopeContext.setCallerPhone propagates to CallerIdentityContext as well.
        // StoreScopeContext.setAudience takes a typed Audience enum — we set audience via
        // CallerIdentityContext.setAudience(String) instead (what the validator reads).
        StoreScopeContext.setCallerPhone(phone);
    }

    // ---- Happy path: DRIVER audience, own id → passes, service called ---------------

    @Test
    void getAllPayouts_driverAudience_ownId_passes() {
        setMcpContext("DRIVER", CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_ID);
        List<Payout> expected = Collections.emptyList();
        when(reconService.getAllPayouts(PayoutType.MESSENGER, FROM, TO_DATE, CALLER_ID))
                .thenReturn(expected);

        List<Payout> result = sut.getAllPayouts(PayoutType.MESSENGER, FROM, TO_DATE, CALLER_ID);

        assertThat(result).isSameAs(expected);
        verify(reconService).getAllPayouts(PayoutType.MESSENGER, FROM, TO_DATE, CALLER_ID);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    // ---- CUSTOMER audience, own id → passes -----------------------------------------

    @Test
    void getAllPayouts_customerAudience_ownId_passes() {
        setMcpContext("CUSTOMER", CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_ID);
        when(reconService.getAllPayouts(any(), any(), any(), eq(CALLER_ID)))
                .thenReturn(Collections.emptyList());

        sut.getAllPayouts(PayoutType.SHOP, FROM, TO_DATE, CALLER_ID);

        verify(reconService).getAllPayouts(PayoutType.SHOP, FROM, TO_DATE, CALLER_ID);
    }

    // ---- ADMIN audience, any id → passes (admin is exempt from ownership check) ------

    @Test
    void getAllPayouts_adminAudience_anyId_passes() {
        setMcpContext("ADMIN", CALLER_PHONE);
        when(reconService.getAllPayouts(any(), any(), any(), eq(OTHER_ID)))
                .thenReturn(Collections.emptyList());

        sut.getAllPayouts(PayoutType.MESSENGER, FROM, TO_DATE, OTHER_ID);

        verify(reconService).getAllPayouts(PayoutType.MESSENGER, FROM, TO_DATE, OTHER_ID);
        verify(mcpCaller, never()).resolveUserId();
    }

    // ---- STORE audience, any id → passes (store is exempt from ownership check) ------

    @Test
    void getAllPayouts_storeAudience_anyId_passes() {
        setMcpContext("STORE", CALLER_PHONE);
        when(reconService.getAllPayouts(any(), any(), any(), eq(OTHER_ID)))
                .thenReturn(Collections.emptyList());

        sut.getAllPayouts(PayoutType.SHOP, FROM, TO_DATE, OTHER_ID);

        verify(reconService).getAllPayouts(PayoutType.SHOP, FROM, TO_DATE, OTHER_ID);
    }

    // ---- DRIVER audience, third-party id → throws + audit recorded ------------------

    @Test
    void getAllPayouts_driverAudience_thirdPartyId_throwsAndAudits() {
        setMcpContext("DRIVER", CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(CALLER_ID);

        assertThatThrownBy(() ->
                sut.getAllPayouts(PayoutType.MESSENGER, FROM, TO_DATE, OTHER_ID))
                .isInstanceOf(CallerIdentityViolationException.class);

        verify(reconService, never()).getAllPayouts(any(), any(), any(), any());
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE),
                eq(OTHER_ID),
                eq(AgentIdentityAuditLog.ViolationType.PAYOUT_NOT_OWNED),
                eq("get_payouts_for_user"),
                eq("DRIVER"),
                isNull(),
                isNull());
    }

    // ---- Null-audience (tokenless) MCP request → throws + audit recorded ------------

    @Test
    void getAllPayouts_nullAudience_mcpRequest_throwsAndAudits() {
        // Tokenless: isMcpRequest=true, no audience
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        // audience stays null

        assertThatThrownBy(() ->
                sut.getAllPayouts(PayoutType.MESSENGER, FROM, TO_DATE, CALLER_ID))
                .isInstanceOf(CallerIdentityViolationException.class);

        verify(reconService, never()).getAllPayouts(any(), any(), any(), any());
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE),
                eq(CALLER_ID),
                eq(AgentIdentityAuditLog.ViolationType.PAYOUT_NOT_OWNED),
                eq("get_payouts_for_user"),
                eq("UNKNOWN"),   // null audience is replaced with "UNKNOWN" in the catch block
                isNull(),
                isNull());
    }

    // ---- Non-MCP REST call → gate is a no-op, service called directly ---------------

    @Test
    void getAllPayouts_nonMcpCall_noGate_serviceCalledDirectly() {
        // isMcpRequest defaults to false (CallerIdentityContext cleared in tearDown / not set here)
        // Gate is bypassed for non-MCP REST callers (AC-04)
        when(reconService.getAllPayouts(any(), any(), any(), eq(CALLER_ID)))
                .thenReturn(Collections.emptyList());

        sut.getAllPayouts(PayoutType.SHOP, FROM, TO_DATE, CALLER_ID);

        verify(reconService).getAllPayouts(PayoutType.SHOP, FROM, TO_DATE, CALLER_ID);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    // ---- DRIVER, null resolved callerId → throws (NOTE-03: null != null must reject) -

    @Test
    void getAllPayouts_driverAudience_nullResolvedCaller_throws() {
        setMcpContext("DRIVER", CALLER_PHONE);
        when(mcpCaller.resolveUserId()).thenReturn(null);  // All prefix lookups failed

        assertThatThrownBy(() ->
                sut.getAllPayouts(PayoutType.MESSENGER, FROM, TO_DATE, CALLER_ID))
                .isInstanceOf(CallerIdentityViolationException.class);

        verify(reconService, never()).getAllPayouts(any(), any(), any(), any());
    }

    // ---- All PayoutType values are routed to service --------------------------------

    @Test
    void getAllPayouts_ambassadorType_delegatesToService() {
        setMcpContext("ADMIN", CALLER_PHONE);
        when(reconService.getAllPayouts(eq(PayoutType.AMBASSADOR), any(), any(), eq(CALLER_ID)))
                .thenReturn(Collections.emptyList());

        sut.getAllPayouts(PayoutType.AMBASSADOR, FROM, TO_DATE, CALLER_ID);

        verify(reconService).getAllPayouts(PayoutType.AMBASSADOR, FROM, TO_DATE, CALLER_ID);
    }

    @Test
    void getAllPayouts_referralPartnerType_delegatesToService() {
        setMcpContext("ADMIN", CALLER_PHONE);
        when(reconService.getAllPayouts(eq(PayoutType.REFERRAL_PARTNER), any(), any(), eq(CALLER_ID)))
                .thenReturn(Collections.emptyList());

        sut.getAllPayouts(PayoutType.REFERRAL_PARTNER, FROM, TO_DATE, CALLER_ID);

        verify(reconService).getAllPayouts(PayoutType.REFERRAL_PARTNER, FROM, TO_DATE, CALLER_ID);
    }
}
