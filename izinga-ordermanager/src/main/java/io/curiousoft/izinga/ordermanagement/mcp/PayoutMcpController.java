package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.recon.payout.Payout;
import io.curiousoft.izinga.recon.payout.PayoutType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Date;
import java.util.List;

/**
 * ADR-026 Phase 5 MCP controller for the payout-read tool.
 *
 * <p>Owns the single payout tool extracted from {@link io.curiousoft.izinga.recon.ReconServiceImpl}:
 * {@code get_payouts_for_user}. Controller-layer concerns (identity gate, audit writes) are
 * handled here before delegating to the service layer.</p>
 *
 * <h3>Gate ordering (ADR-026 F1 / ADR-024)</h3>
 * <ul>
 *   <li>get_payouts_for_user: {@link StoreScopeValidator#validateCallerOwnsId} with audit.
 *       Same logic as the original {@code CallerIdentityValidator.validateCallerOwnsId} call
 *       in {@code ReconServiceImpl} — both delegate to the same commons implementation.
 *       Null-audience hard reject happens inside {@code validateCallerOwnsId}; the audit
 *       is written in the catch (matches the original behaviour where the catch runs for
 *       both null-audience and owner-mismatch cases).</li>
 * </ul>
 *
 * <h3>resolveCallerUserId → McpCaller.resolveUserId equivalence</h3>
 * <p>The private {@code resolveCallerUserId()} in {@code ReconServiceImpl} and
 * {@link McpCaller#resolveUserId()} both perform the same prefix-loop lookup
 * ("0", "+27", "27") against {@code UserProfileRepo.findByMobileNumber}. This controller
 * uses {@code mcpCaller::resolveUserId} as the supplier — identical behaviour.</p>
 *
 * <h3>AC-04 compliance</h3>
 * <p>{@link io.curiousoft.izinga.recon.ReconController} (REST) calls
 * {@code ReconService.getAllPayouts()} directly on the ungated service. The gate is in
 * this controller only — REST callers are unaffected.</p>
 *
 * <h3>R5 compliance</h3>
 * <p>This class depends on {@link ReconService} (a service interface), never on any
 * {@code *Repo} or {@code *Repository} type directly.</p>
 */
@McpToolController
public class PayoutMcpController {

    // ---- Exact refusal message literals (must match CallerIdentityValidator.kt) -----------

    static final String UNAUTHENTICATED_MESSAGE =
        "IDENTITY_REFUSAL: This tool requires an authenticated scope token. " +
        "Unauthenticated or tokenless requests are not permitted on the MCP surface. " +
        "Please contact iZinga support if you need further help.";

    static final String VIOLATION_MESSAGE =
        "IDENTITY_REFUSAL: I can only assist with queries for your own account. " +
        "For privacy and security reasons, I am not authorised to access another person's information. " +
        "Please contact iZinga support if you need further help.";

    // ---- Collaborators ------------------------------------------------------------------

    private final ReconService reconService;
    private final McpCaller mcpCaller;
    private final AgentIdentityAuditService agentIdentityAuditService;

    public PayoutMcpController(ReconService reconService,
                                McpCaller mcpCaller,
                                AgentIdentityAuditService agentIdentityAuditService) {
        this.reconService = reconService;
        this.mcpCaller = mcpCaller;
        this.agentIdentityAuditService = agentIdentityAuditService;
    }

    // ---- get_payouts_for_user -----------------------------------------------------------

    /**
     * Gets all payouts for a given payout type, date range and toId.
     *
     * <p>Gate: ADR-024 — CUSTOMER/DRIVER agents may only query payouts for their own toId.
     * Uses {@link StoreScopeValidator#validateCallerOwnsId} which checks isMcpRequest,
     * rejects null-audience, passes ADMIN/STORE/OTP, and enforces ownership for
     * CUSTOMER/DRIVER. Writes an async audit record on violation (including null-audience).
     * This matches the original Kotlin gate in {@code ReconServiceImpl.getAllPayouts}.</p>
     */
    @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(name = "get_payouts_for_user", description = "Get all payouts for a given payout type, date range and toId. The default date range is past 7 days. " +
            "The Payout will have all orders for this payout and the total amount for the payout. " +
            "For driver the payoutType is MESSENGER and the toId is the driver id. " +
            "For shop the payoutType is SHOP and the toId is the shop id.")
    public List<Payout> getAllPayouts(
            @ToolParam(description = "For driver the payoutType is MESSENGER") PayoutType payoutType,
            @ToolParam(description = "Date time format is like 2026-04-09T00:00:00.000+00:00") Date from,
            @ToolParam(description = "Date time format is like 2026-04-09T00:00:00.000+00:00") Date toDate,
            @ToolParam(description = "This is the Driver/Messenger id to use for lookup") String toId) {
        // Gate: ADR-024: CUSTOMER/DRIVER agents may only query payouts for their own toId.
        // StoreScopeValidator.validateCallerOwnsId delegates to CallerIdentityValidator which
        // hard-rejects null-audience (tokenless) MCP requests before any ownership check.
        try {
            StoreScopeValidator.validateCallerOwnsId(toId, mcpCaller::resolveUserId);
        } catch (CallerIdentityViolationException e) {
            // FIX-04: capture audience on the request thread before the @Async audit dispatch
            String auditAudience = CallerIdentityContext.getAudience() != null
                    ? CallerIdentityContext.getAudience() : "UNKNOWN";
            agentIdentityAuditService.recordViolation(
                    StoreScopeContext.getCallerPhone(), toId,
                    AgentIdentityAuditLog.ViolationType.PAYOUT_NOT_OWNED, "get_payouts_for_user",
                    auditAudience, null, null);
            throw e;
        }
        return reconService.getAllPayouts(payoutType, from, toDate, toId);
    }
}
