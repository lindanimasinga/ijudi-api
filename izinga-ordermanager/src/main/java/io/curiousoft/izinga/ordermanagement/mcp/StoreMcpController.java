package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.AudienceViolationException;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * ADR-026 Phase 4 MCP controller for store-query tools.
 *
 * <p>Owns the three store tools extracted from {@link StoreService}:
 * {@code find_store_or_shops_by_id}, {@code find_stores_by_owner}, and {@code find_stores}.
 * Controller-layer concerns (identity gates, audience restrictions, audit writes, and
 * bank-account masking) are handled here before and after delegating to the service layer.</p>
 *
 * <h3>Gate ordering and masking per tool (ADR-026 F1 / REQ-08 / SA-021-8)</h3>
 * <ul>
 *   <li>find_store_or_shops_by_id: Gate 1 = {@link StoreScopeValidator#validate(String)} (store scope,
 *       throws StoreScopeViolationException, no audit); Gate 2 = {@link StoreScopeValidator#validateAudience}
 *       (throws AudienceViolationException, no audit). MCP-conditional bank masking applied after service call.</li>
 *   <li>find_stores_by_owner: full AMD-01 audience switch with audit (null→reject, STORE→owner-match,
 *       ADMIN→pass, others→reject). MCP-conditional bank masking applied after service call.</li>
 *   <li>find_stores: {@link StoreScopeValidator#validateAudience} gate (throws AudienceViolationException).
 *       Unconditional bank masking and markUp applied after service call (no non-MCP callers per F1).</li>
 * </ul>
 *
 * <h3>Masking contract (REQ-08)</h3>
 * <p>Masking happens AFTER any service call (no save follows), so the persistence hazard
 * (masked entity being saved to Mongo) is eliminated. Services return unmasked entities;
 * controllers mask before returning to the AI agent.</p>
 *
 * <h3>R5 compliance</h3>
 * <p>This class depends on {@link StoreService} (a service), never on any
 * {@code *Repo} or {@code *Repository} type directly.</p>
 */
@McpToolController
public class StoreMcpController {

    private static final Logger LOG = LoggerFactory.getLogger(StoreMcpController.class);

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

    private final StoreService storeService;
    private final AgentIdentityAuditService agentIdentityAuditService;
    private final String mainPayAccount;
    private final double markupPercentage;

    public StoreMcpController(StoreService storeService,
                               AgentIdentityAuditService agentIdentityAuditService,
                               @Value("${ukheshe.main.account}") String mainPayAccount,
                               @Value("${service.markup.perc}") double markupPercentage) {
        this.storeService = storeService;
        this.agentIdentityAuditService = agentIdentityAuditService;
        this.mainPayAccount = mainPayAccount;
        this.markupPercentage = markupPercentage;
    }

    // ---- find_store_or_shops_by_id ------------------------------------------------------

    /**
     * Finds a store profile by its ID.
     *
     * <p>Gate 1: SA-021-8 — {@link StoreScopeValidator#validate(String)} enforces store scope;
     * throws {@link io.curiousoft.izinga.ordermanagement.security.StoreScopeViolationException}
     * when the token is locked to a different store. No audit is written here.
     *
     * <p>Gate 2: SEC-MCP-02 (REQ-06) — on MCP requests, requires an authenticated audience.
     * AMD-01 (REQ-A10): ADMIN added to permitted set.
     * Throws {@link AudienceViolationException}. No audit is written here.
     *
     * <p>REQ-08: Bank account masking applied AFTER service call (MCP-conditional).
     */
    @McpAudience({Audience.STORE, Audience.DRIVER, Audience.CUSTOMER, Audience.ADMIN})
    @Tool(name = "find_store_or_shops_by_id", description = "Find a store profile by its ID. If the store has no business hours set, default hours will be added.")
    public StoreProfile findStore(String id) {
        // Gate 1: SA-021-8: enforce store scope — throws StoreScopeViolationException if this
        // request's scope token is locked to a different store. No audit written.
        StoreScopeValidator.validate(id);
        // Gate 2: SEC-MCP-02 (REQ-06): on MCP requests, require an authenticated audience.
        // AMD-01 (REQ-A10): ADMIN added to permitted set for find_store_or_shops_by_id.
        if (CallerIdentityContext.isMcpRequest()) {
            StoreScopeValidator.validateAudience(Audience.STORE, Audience.DRIVER, Audience.CUSTOMER, Audience.ADMIN);
        }
        StoreProfile store = storeService.find(id);
        // REQ-08: mask bank accountId only on MCP requests to prevent round-trip corruption
        // (REST callers such as the onboarding app GET-then-PATCH the store; masking there
        // would overwrite the real settlement account).
        // Applied AFTER service call so the masked value is never written to Mongo.
        if (CallerIdentityContext.isMcpRequest() && store != null && store.getBank() != null) {
            store.getBank().setAccountId(mainPayAccount);
        }
        return store;
    }

    // ---- find_stores_by_owner -----------------------------------------------------------

    /**
     * Finds all store profiles owned by a specific user ID.
     *
     * <p>Gate: SEC-MCP-02 (CORRECTION 2) / AMD-01 (REQ-A8) — on MCP requests:
     * null audience → IDENTITY_REFUSAL+audit; STORE → permitted only when store scope set AND
     * the store's owner matches ownerId; ADMIN → pass-through; others → IDENTITY_REFUSAL+audit.
     *
     * <p>REQ-08: Bank account masking applied AFTER service call (MCP-conditional).
     */
    @McpAudience({Audience.STORE, Audience.ADMIN})
    @Tool(name = "find_stores_by_owner", description = "Find all store profiles owned by a specific user ID.")
    public List<StoreProfile> findByOwner(String ownerId) {
        // Gate: SEC-MCP-02 (CORRECTION 2): on MCP requests, enforce caller-ownership or store-scope.
        if (CallerIdentityContext.isMcpRequest()) {
            String audience = CallerIdentityContext.getAudience();
            if (audience == null) {
                agentIdentityAuditService.recordViolation(
                        StoreScopeContext.getCallerPhone(), ownerId,
                        AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_stores_by_owner",
                        "UNKNOWN", null, null);
                throw new CallerIdentityViolationException(UNAUTHENTICATED_MESSAGE);
            }
            if ("STORE".equals(audience)) {
                // STORE audience: permitted only when a store scope is set AND the store's owner matches
                String permittedStoreId = StoreScopeContext.getPermittedStoreId();
                if (permittedStoreId == null) {
                    agentIdentityAuditService.recordViolation(
                            StoreScopeContext.getCallerPhone(), ownerId,
                            AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_stores_by_owner",
                            audience, null, null);
                    throw new CallerIdentityViolationException(VIOLATION_MESSAGE);
                }
                // ADR-026 Step B: use findByIdNoSideEffects — the ownership probe must NOT trigger
                // the lazy-seed of default business hours (which calls profileRepo.save). R5 is
                // satisfied: this is a service method, not a direct repository call.
                StoreProfile permitted = storeService.findByIdNoSideEffects(permittedStoreId);
                if (permitted == null || !ownerId.equals(permitted.getOwnerId())) {
                    agentIdentityAuditService.recordViolation(
                            StoreScopeContext.getCallerPhone(), ownerId,
                            AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_stores_by_owner",
                            audience, null, null);
                    throw new CallerIdentityViolationException(VIOLATION_MESSAGE);
                }
            } else if ("ADMIN".equals(audience)) {
                // AMD-01 (REQ-A8): ADMIN has broad access — pass through without ownership check
                LOG.debug("[MCP-ADMIN] find_stores_by_owner pass-through for ADMIN audience");
            } else {
                // AMD-01 (REQ-A8): any other audience (CUSTOMER, DRIVER, etc.) is a hard reject.
                agentIdentityAuditService.recordViolation(
                        StoreScopeContext.getCallerPhone(), ownerId,
                        AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_stores_by_owner",
                        audience, null, null);
                throw new CallerIdentityViolationException(VIOLATION_MESSAGE);
            }
        }
        List<StoreProfile> stores = storeService.findByOwner(ownerId);
        // REQ-08: mask bank accountId only on MCP requests.
        if (CallerIdentityContext.isMcpRequest()) {
            stores.forEach(s -> {
                if (s.getBank() != null) {
                    s.getBank().setAccountId(mainPayAccount);
                }
            });
        }
        return stores;
    }

    // ---- find_stores --------------------------------------------------------------------

    /**
     * Finds stores with optional filters.
     *
     * <p>Gate: SEC-MCP-02 (REQ-05) / AMD-01 (REQ-A10) — on MCP requests, requires an
     * authenticated audience from the permitted set [CUSTOMER, DRIVER, STORE, ADMIN].
     * Throws {@link AudienceViolationException}. No audit is written here.
     *
     * <p>REQ-08: Bank masking and markUp applied AFTER service call (unconditional, since
     * this method has no non-MCP REST callers per ADR-026 F1).
     */
    @McpAudience({Audience.CUSTOMER, Audience.DRIVER, Audience.STORE, Audience.ADMIN})
    @Tool(
        name = "find_stores",
        description = "Find stores with optional filters. " +
            "All parameters are optional — pass null to skip a filter. " +
            "city: case-insensitive substring of the store address. " +
            "storeType: one of FOOD, CLOTHING, SALON, CAR_WASH, MOVERS, TIPS, LICENSING, PARTS. " +
            "activeStatus: true = only available (non-OFFLINE) stores, false = only OFFLINE stores, null = all."
    )
    public List<StoreProfile> findStores(
            @ToolParam(required = false, description = "Optional city name — case-insensitive substring match against store address. Pass null to skip.") String city,
            @ToolParam(required = false, description = "Optional store type — one of FOOD, CLOTHING, SALON, CAR_WASH, MOVERS, TIPS, LICENSING, PARTS. Pass null to skip.") StoreType storeType,
            @ToolParam(required = false, description = "Optional availability filter — true = non-OFFLINE stores only, false = OFFLINE stores only, null = no filter.") Boolean activeStatus) {
        // Gate: SEC-MCP-02 (REQ-05 / find_stores): on MCP requests, require an authenticated audience.
        // AMD-01 (REQ-A10): ADMIN added to permitted set for find_stores.
        if (CallerIdentityContext.isMcpRequest()) {
            StoreScopeValidator.validateAudience(Audience.CUSTOMER, Audience.DRIVER, Audience.STORE, Audience.ADMIN);
        }
        List<StoreProfile> result = storeService.findStores(city, storeType, activeStatus);
        // REQ-08: apply bank masking and markUp after service call.
        // findStores has no non-MCP REST callers (ADR-026 F1), so this is applied unconditionally
        // (matching the original behaviour on this code path).
        result.forEach(profile -> {
            if (profile.getBank() != null) {
                profile.getBank().setAccountId(mainPayAccount);
            }
            profile.setMarkUp(markupPercentage);
        });
        return result;
    }
}
