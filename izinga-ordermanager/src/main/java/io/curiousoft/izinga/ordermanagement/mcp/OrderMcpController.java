package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Set;

/**
 * ADR-026 Phase 3 MCP controller for order-read tools.
 *
 * <p>Owns the five order-read tools extracted from {@link OrderServiceImpl}:
 * {@code find_order_by_id}, {@code find_orders_by_user_id}, {@code find_order_for_messenger},
 * {@code find_orders_by_phone_number}, and {@code find_orders_by_messenger_id}.
 * Controller-layer concerns (identity gates, audience restrictions, and audit writes)
 * are handled here before delegating to the service layer.</p>
 *
 * <h3>Gate ordering per tool (ADR-026 F1 / ADR-024)</h3>
 * <ul>
 *   <li>find_order_by_id: null-audience hard reject (with audit + throw) BEFORE repo access;
 *       then CUSTOMER/DRIVER ownership check using {@link McpCaller#resolveUserId()}.</li>
 *   <li>find_orders_by_user_id: {@link StoreScopeValidator#validateCallerOwnsId} with audit.</li>
 *   <li>find_order_for_messenger: null-audience hard reject (with audit + throw) BEFORE repo access;
 *       then DRIVER checks messengerId, CUSTOMER checks customerId.</li>
 *   <li>find_orders_by_phone_number: {@link StoreScopeValidator#validateCallerPhone} with audit;
 *       length guard after gate.</li>
 *   <li>find_orders_by_messenger_id: full AMD-01 audience switch: null → IDENTITY_REFUSAL+audit,
 *       DRIVER → validateCallerOwnsId+audit, ADMIN → pass-through (LOG.debug),
 *       others → IDENTITY_REFUSAL+audit.</li>
 * </ul>
 *
 * <h3>R5 compliance</h3>
 * <p>This class depends on {@link OrderServiceImpl} and {@link McpCaller} (services), never on
 * any {@code *Repo} or {@code *Repository} type directly.</p>
 */
@McpToolController
public class OrderMcpController {

    private static final Logger LOG = LoggerFactory.getLogger(OrderMcpController.class);

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

    private final OrderServiceImpl orderService;
    private final McpCaller mcpCaller;
    private final AgentIdentityAuditService agentIdentityAuditService;

    public OrderMcpController(OrderServiceImpl orderService,
                               McpCaller mcpCaller,
                               AgentIdentityAuditService agentIdentityAuditService) {
        this.orderService = orderService;
        this.mcpCaller = mcpCaller;
        this.agentIdentityAuditService = agentIdentityAuditService;
    }

    // ---- find_order_by_id ---------------------------------------------------------------

    /**
     * Finds an order by its ID.
     *
     * <p>Gate 1: SEC-MCP-01 / SEC-MCP-BUG-01 — null-audience hard reject BEFORE any
     * repository access so a tokenless caller cannot probe order-ID existence
     * (i.e. cannot distinguish "order not found" from IDENTITY_REFUSAL).
     * Writes an async audit record then throws.
     *
     * <p>Gate 2: ADR-024 — CUSTOMER must own the order; DRIVER must be assigned to it.
     * Writes an async audit record on violation.
     */
    @McpAudience({Audience.CUSTOMER, Audience.DRIVER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(name = "find_order_by_id", description = "Find order by id and return the order details")
    public Order findOrder(String orderId) {
        // Gate 1: SEC-MCP-01 / SEC-MCP-BUG-01: tokenless MCP request — hard reject BEFORE any
        // repository access so a tokenless caller cannot probe order-ID existence
        // (i.e. cannot distinguish "order not found" from IDENTITY_REFUSAL).
        if (CallerIdentityContext.isMcpRequest() && CallerIdentityContext.getAudience() == null) {
            agentIdentityAuditService.recordViolation(
                    StoreScopeContext.getCallerPhone(), orderId,
                    AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_order_by_id",
                    "UNKNOWN", null, null);
            throw new CallerIdentityViolationException(UNAUTHENTICATED_MESSAGE);
        }
        Order order = orderService.findOrder(orderId);
        if (order != null) {
            // Gate 2: ADR-024: CUSTOMER must own the order; DRIVER must be assigned to it.
            String audience = CallerIdentityContext.getAudience();
            try {
                if ("CUSTOMER".equals(audience)) {
                    StoreScopeValidator.validateCallerOwnsId(order.getCustomerId(), mcpCaller::resolveUserId);
                } else if ("DRIVER".equals(audience)) {
                    String messengerId = order.getShippingData() != null ? order.getShippingData().getMessengerId() : null;
                    StoreScopeValidator.validateCallerOwnsId(messengerId, mcpCaller::resolveUserId);
                }
            } catch (CallerIdentityViolationException e) {
                // FIX-04: capture audience on the request thread before the @Async audit dispatch
                agentIdentityAuditService.recordViolation(
                        StoreScopeContext.getCallerPhone(), orderId,
                        AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED, "find_order_by_id",
                        audience != null ? audience : "UNKNOWN", null, null);
                throw e;
            }
        }
        return order;
    }

    // ---- find_orders_by_user_id ---------------------------------------------------------

    /**
     * Finds orders by user ID.
     *
     * <p>Gate: ADR-024 — CUSTOMER/DRIVER agents may only fetch orders for their own user ID.
     * Uses {@link StoreScopeValidator#validateCallerOwnsId} which checks isMcpRequest internally.
     * Writes an async audit record on violation.
     */
    @McpAudience({Audience.CUSTOMER, Audience.DRIVER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(name = "find_orders_by_user_id", description = "Find orders by user id and return the order details")
    public List<Order> findOrderByUserId(String userId) {
        // Gate: ADR-024: CUSTOMER/DRIVER agents may only fetch orders for their own user ID.
        try {
            StoreScopeValidator.validateCallerOwnsId(userId, mcpCaller::resolveUserId);
        } catch (CallerIdentityViolationException e) {
            // FIX-04: capture audience on the request thread before the @Async dispatch
            String auditAudience = CallerIdentityContext.getAudience() != null
                    ? CallerIdentityContext.getAudience() : "UNKNOWN";
            agentIdentityAuditService.recordViolation(
                    StoreScopeContext.getCallerPhone(), userId,
                    AgentIdentityAuditLog.ViolationType.ID_NOT_OWNED, "find_orders_by_user_id",
                    auditAudience, null, null);
            throw e;
        }
        return orderService.findOrderByUserId(userId);
    }

    // ---- find_order_for_messenger -------------------------------------------------------

    /**
     * Finds an order by ID and returns it as a MessengerOrderDto.
     *
     * <p>Gate 1: SEC-MCP-01 / SEC-MCP-BUG-01 — null-audience hard reject BEFORE any
     * repository access so a tokenless caller cannot probe order-ID existence.
     * Writes an async audit record then throws.
     *
     * <p>Gate 2: ADR-024 — DRIVER must be assigned to this order; CUSTOMER must own it (FIX-03).
     * Writes an async audit record on violation.
     */
    @McpAudience({Audience.CUSTOMER, Audience.DRIVER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(name = "find_order_for_messenger", description = "Find order by id and return the order details as MessengerOrderDto")
    public Order findOrderForMessenger(String orderId) {
        // Gate 1: SEC-MCP-01 / SEC-MCP-BUG-01: tokenless MCP request — hard reject BEFORE any
        // repository access so a tokenless caller cannot probe order-ID existence
        // (i.e. cannot distinguish "Order not found" from IDENTITY_REFUSAL).
        String audience = CallerIdentityContext.getAudience();
        if (CallerIdentityContext.isMcpRequest() && audience == null) {
            agentIdentityAuditService.recordViolation(
                    StoreScopeContext.getCallerPhone(), orderId,
                    AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_order_for_messenger",
                    "UNKNOWN", null, null);
            throw new CallerIdentityViolationException(UNAUTHENTICATED_MESSAGE);
        }
        // Gate 2: ADR-024: DRIVER must be assigned to this order; CUSTOMER must own it (FIX-03).
        Order order = orderService.findOrderForMessenger(orderId);
        if ("DRIVER".equals(audience)) {
            try {
                String messengerId = order.getShippingData() != null ? order.getShippingData().getMessengerId() : null;
                StoreScopeValidator.validateCallerOwnsId(messengerId, mcpCaller::resolveUserId);
            } catch (CallerIdentityViolationException e) {
                // FIX-04: audience already captured as local var on request thread
                agentIdentityAuditService.recordViolation(
                        StoreScopeContext.getCallerPhone(), orderId,
                        AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED, "find_order_for_messenger",
                        audience != null ? audience : "UNKNOWN", null, null);
                throw e;
            }
        } else if ("CUSTOMER".equals(audience)) {
            // FIX-03 (Legal C-4): CUSTOMER audience must own the order — prevents cross-customer
            // data leak of full MessengerOrderDto (shipping details, iZinga commission, etc.).
            try {
                StoreScopeValidator.validateCallerOwnsId(order.getCustomerId(), mcpCaller::resolveUserId);
            } catch (CallerIdentityViolationException e) {
                // FIX-04: audience already captured as local var on request thread
                agentIdentityAuditService.recordViolation(
                        StoreScopeContext.getCallerPhone(), orderId,
                        AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED, "find_order_for_messenger",
                        audience, null, null);
                throw e;
            }
        }
        return order;
    }

    // ---- find_orders_by_phone_number ----------------------------------------------------

    /**
     * Finds orders by phone number.
     *
     * <p>Gate: ADR-024 — CUSTOMER/DRIVER agents may only query orders for their own phone.
     * Uses {@link StoreScopeValidator#validateCallerPhone} which checks isMcpRequest internally.
     * Writes an async audit record on violation.
     */
    @McpAudience({Audience.CUSTOMER, Audience.DRIVER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(name = "find_orders_by_phone_number", description = "Find orders by phone number and return the order details")
    public List<Order> findOrderByPhone(String phone) {
        // Gate: ADR-024: CUSTOMER/DRIVER agents may only query orders for their own phone.
        try {
            StoreScopeValidator.validateCallerPhone(phone);
        } catch (CallerIdentityViolationException e) {
            // FIX-04: capture audience on the request thread before the @Async dispatch
            String auditAudience = CallerIdentityContext.getAudience() != null
                    ? CallerIdentityContext.getAudience() : "UNKNOWN";
            agentIdentityAuditService.recordViolation(
                    StoreScopeContext.getCallerPhone(), phone,
                    AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH, "find_orders_by_phone_number",
                    auditAudience, null, null);
            throw e;
        }
        return orderService.findOrderByPhone(phone);
    }

    // ---- find_orders_by_messenger_id ----------------------------------------------------

    /**
     * Finds orders assigned to a messenger by their ID.
     *
     * <p>Gate (AMD-01 / SEC-MCP-02): on MCP requests enforce caller-scope gates:
     * null audience → IDENTITY_REFUSAL+audit, DRIVER → validateCallerOwnsId+audit,
     * ADMIN → pass-through (LOG.debug), others → IDENTITY_REFUSAL+audit.
     */
    @McpAudience({Audience.DRIVER, Audience.ADMIN})
    @Tool(name = "find_orders_by_messenger_id", description = "Find orders by messenger id and return the order details as MessengerOrderDto")
    public Set<Order> findOrderByMessengerId(String id,
                                              @ToolParam(required = false) boolean allStages) {
        // SEC-MCP-02 (CORRECTION 2): on MCP requests enforce caller-scope gates.
        if (CallerIdentityContext.isMcpRequest()) {
            String audience = CallerIdentityContext.getAudience();
            if (audience == null) {
                agentIdentityAuditService.recordViolation(
                        StoreScopeContext.getCallerPhone(), id,
                        AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_orders_by_messenger_id",
                        "UNKNOWN", null, null);
                throw new CallerIdentityViolationException(UNAUTHENTICATED_MESSAGE);
            }
            if ("DRIVER".equals(audience)) {
                // DRIVER may only retrieve their own orders (caller userId == requested messenger id)
                try {
                    StoreScopeValidator.validateCallerOwnsId(id, mcpCaller::resolveUserId);
                } catch (CallerIdentityViolationException e) {
                    agentIdentityAuditService.recordViolation(
                            StoreScopeContext.getCallerPhone(), id,
                            AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED, "find_orders_by_messenger_id",
                            audience, null, null);
                    throw e;
                }
            } else if ("ADMIN".equals(audience)) {
                // AMD-01 (REQ-A7): ADMIN has broad access — no ownership check, no store filter
                LOG.debug("[MCP-ADMIN] find_orders_by_messenger_id pass-through for ADMIN audience");
            } else {
                // STORE, CUSTOMER and any other audience are not permitted to enumerate driver orders
                // AMD-01 (REQ-A7): STORE path removed; STORE is now a hard reject with audit record
                agentIdentityAuditService.recordViolation(
                        StoreScopeContext.getCallerPhone(), id,
                        AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_orders_by_messenger_id",
                        audience, null, null);
                throw new CallerIdentityViolationException(VIOLATION_MESSAGE);
            }
        }
        return orderService.findOrderByMessengerId(id, allStages);
    }
}
