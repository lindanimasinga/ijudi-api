package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.orders.McpOrderService;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

/**
 * ADR-026 Phase 1 MCP controller for order-creation tools.
 *
 * <p>Owns the {@code start_order} tool, extracted from {@link McpOrderService}. The
 * controller-layer concerns (audience gate, consent, idempotency-key presence, customer
 * existence, and caller-ownership enforcement) are handled here before delegating the
 * actual order-creation logic to {@link McpOrderService}.</p>
 *
 * <h3>Gate ordering (SA-022-01 + ADR-026 R-ORD-01)</h3>
 * <ol>
 *   <li>Audience gate — tokenless or disallowed audience returns REJECTED immediately.</li>
 *   <li>Consent gate — {@code customerConfirmed} must be true.</li>
 *   <li>Idempotency-key presence — must not be blank.</li>
 *   <li>Customer existence — {@code customerId} must resolve to a UserProfile.</li>
 *   <li>Caller ownership — CUSTOMER-audience callers may only create orders for themselves.</li>
 *   <li>Delegate to {@link McpOrderService#startOrder} for dedup and domain logic.</li>
 * </ol>
 *
 * <h3>R5 compliance</h3>
 * <p>This class depends on {@link McpOrderService} and {@link McpCaller} (services), never on
 * any {@code *Repo} or {@code *Repository} type directly.</p>
 */
@McpToolController
public class OrderCreationMcpController {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCreationMcpController.class);

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

    private final McpOrderService mcpOrderService;
    private final McpCaller mcpCaller;
    private final AgentIdentityAuditService agentIdentityAuditService;

    public OrderCreationMcpController(McpOrderService mcpOrderService,
                                       McpCaller mcpCaller,
                                       AgentIdentityAuditService agentIdentityAuditService) {
        this.mcpOrderService = mcpOrderService;
        this.mcpCaller = mcpCaller;
        this.agentIdentityAuditService = agentIdentityAuditService;
    }

    // ---- Tool method --------------------------------------------------------------------

    /**
     * Creates a new order on behalf of an authenticated customer.
     *
     * <p>Guards are evaluated in the order documented in this class's Javadoc before
     * delegating to {@link McpOrderService#startOrder}. Steps 1-3 (consent, idempotency-key
     * presence, customer existence) are re-run inside the service call — this double-check
     * is harmless and ensures that the service layer remains independently safe.
     *
     * @see McpOrderService#startOrder for the full parameter contract
     */
    @McpAudience({Audience.CUSTOMER, Audience.STORE, Audience.ADMIN})
    @Tool(
        name = "start_order",
        description = "Creates a new order for a customer. " +
            "customerConfirmed MUST be true — only call this after the customer has explicitly confirmed they want to place the order. " +
            "idempotencyKey MUST be unique per order attempt; duplicate calls within 24 hours return the existing order. " +
            "orderType must be ONLINE or INSTORE. " +
            "basketJson is a JSON-serialised Basket with an items array. " +
            "shippingDataJson is a JSON-serialised ShippingData (required for ONLINE orders, null for INSTORE)."
    )
    public McpOrderService.StartOrderResponse startOrder(
            @ToolParam(description = "The customer's profile ID") String customerId,
            @ToolParam(description = "Must be true — agent must have confirmed the customer's intent before placing the order") boolean customerConfirmed,
            @ToolParam(description = "Unique key for this order attempt to prevent duplicate orders") String idempotencyKey,
            @ToolParam(description = "The store/shop ID to place the order against") String shopId,
            @ToolParam(description = "Brief description of the order") String description,
            @ToolParam(description = "Order type: ONLINE or INSTORE") String orderType,
            @ToolParam(description = "JSON of the Basket with items array, e.g. {\"items\":[{\"name\":\"chips\",\"quantity\":2,\"price\":10,\"totalPrice\":20}]}") String basketJson,
            @ToolParam(required = false, description = "JSON of ShippingData (required for ONLINE orders). null for INSTORE orders.") String shippingDataJson) {

        // Step 0 — audience gate: reject tokenless MCP requests and disallowed audiences.
        // Evidence: StoreAiAgent mints STORE audience tokens; AiCustomerServiceAgent's system prompt
        // lists start_order tools for CUSTOMER audience. DRIVER agents have no legitimate order-creation use.
        if (CallerIdentityContext.isMcpRequest()) {
            String audience = CallerIdentityContext.getAudience();
            if (audience == null) {
                LOG.warn("start_order: HARD-REJECT — MCP request has no scope token (null audience) for customerId={}", customerId);
                return McpOrderService.StartOrderResponse.rejected(UNAUTHENTICATED_MESSAGE);
            }
            // AMD-01 (REQ-A9): ADMIN is also permitted to create orders
            if (!"CUSTOMER".equals(audience) && !"STORE".equals(audience) && !"ADMIN".equals(audience)) {
                LOG.warn("start_order: HARD-REJECT — audience={} is not permitted to create orders for customerId={}", audience, customerId);
                return McpOrderService.StartOrderResponse.rejected(VIOLATION_MESSAGE);
            }
        }

        // Step 1 — consent gate (SA-022-01 Guard 2)
        if (!customerConfirmed) {
            return McpOrderService.StartOrderResponse.rejected(
                "Customer confirmation not recorded. Obtain explicit order confirmation before placing the order.");
        }

        // Step 2 — idempotency key presence (SA-022-01 Guard 3a)
        if (!StringUtils.hasText(idempotencyKey)) {
            return McpOrderService.StartOrderResponse.rejected(
                "Idempotency key is required. Provide a unique key for this order attempt.");
        }

        // Step 3 — customer existence (SA-022-01 Guard 1): cheap check before ownership check
        if (!mcpOrderService.customerExists(customerId)) {
            return McpOrderService.StartOrderResponse.rejected(
                "No customer account found for the provided ID.");
        }

        // Step 4 — caller ownership: CUSTOMER agents may only create orders for themselves.
        // ADMIN and STORE audiences are exempt (see CallerIdentityValidator.validateCallerOwnsId).
        try {
            StoreScopeValidator.validateCallerOwnsId(customerId, mcpCaller::resolveUserId);
        } catch (CallerIdentityViolationException e) {
            // FIX-04: capture audience on the request thread before the @Async audit dispatch
            String auditAudience = CallerIdentityContext.getAudience() != null
                ? CallerIdentityContext.getAudience() : "UNKNOWN";
            agentIdentityAuditService.recordViolation(
                CallerIdentityContext.getCallerPhone(), customerId,
                AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED, "start_order",
                auditAudience, null, null);
            return McpOrderService.StartOrderResponse.rejected(e.getMessage());
        }

        // Step 5 — delegate to service layer (Steps 1-3 re-run harmlessly; dedup + domain execute there)
        return mcpOrderService.startOrder(customerId, customerConfirmed, idempotencyKey,
                shopId, description, orderType, basketJson, shippingDataJson);
    }
}
