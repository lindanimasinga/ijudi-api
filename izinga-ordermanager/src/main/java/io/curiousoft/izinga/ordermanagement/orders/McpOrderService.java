package io.curiousoft.izinga.ordermanagement.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * MCP-layer service that exposes order-creation as a tool callable by AI agents.
 *
 * <p>Follows the {@code DocumentUploadMcpService} precedent: MCP-specific concerns
 * (identity validation, consent gate, idempotency) are handled here, keeping
 * {@link OrderServiceImpl} free of tool-layer logic.
 *
 * <p>Security rules enforced per SA-022-01 (documented in McpConfig):
 * <ol>
 *   <li>Guard 1 — identity: {@code customerId} must resolve to an existing UserProfile.</li>
 *   <li>Guard 2 — consent: {@code customerConfirmed} must be {@code true}.</li>
 *   <li>Guard 3 — idempotency: duplicate calls within the configurable window return
 *       the existing order rather than creating a second one.</li>
 * </ol>
 *
 * <p><strong>Known gap (SA-022-01):</strong> {@code customerId} is validated for existence
 * but is not yet cryptographically bound to the caller's scope token. A malicious agent
 * could supply a valid but different customer's ID. This strengthening is deferred —
 * see the SA-022-01 Javadoc in {@code McpConfig} for the full rationale.
 */
@Service
public class McpOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(McpOrderService.class);

    private final OrderServiceImpl orderService;
    private final UserProfileRepo userProfileRepo;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final long idempotencyWindowHours;

    public McpOrderService(OrderServiceImpl orderService,
                           UserProfileRepo userProfileRepo,
                           OrderRepository orderRepository,
                           ObjectMapper objectMapper,
                           @Value("${mcp.order.idempotency.window.hours:24}") long idempotencyWindowHours) {
        this.orderService = orderService;
        this.userProfileRepo = userProfileRepo;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.idempotencyWindowHours = idempotencyWindowHours;
    }

    /**
     * Creates a new order on behalf of an authenticated customer.
     *
     * <p>Three mandatory guards are evaluated before reaching domain logic (SA-022-01):
     * <ol>
     *   <li>Customer identity verification</li>
     *   <li>Explicit customer confirmation</li>
     *   <li>Idempotency-key deduplication within the configured window</li>
     * </ol>
     *
     * @param customerId        The customer's profile ID (must resolve to an existing UserProfile).
     * @param customerConfirmed Must be {@code true}; the agent MUST have obtained explicit order
     *                          confirmation from the customer before passing this flag.
     * @param idempotencyKey    A unique key for this order attempt. Duplicate calls within the
     *                          window return the existing order rather than creating a second one.
     * @param shopId            The ID of the store the order is placed against.
     * @param description       A brief human-readable description of the order.
     * @param orderType         The order type: "ONLINE" (with delivery or collection) or "INSTORE".
     * @param basketJson        JSON-serialised Basket object (items array required).
     * @param shippingDataJson  JSON-serialised ShippingData object. Required for ONLINE orders;
     *                          may be null or blank for INSTORE orders.
     * @return a {@link StartOrderResponse} indicating success or the reason for rejection.
     */
    @Tool(
        name = "start_order",
        description = "Creates a new order for a customer. " +
            "customerConfirmed MUST be true — only call this after the customer has explicitly confirmed they want to place the order. " +
            "idempotencyKey MUST be unique per order attempt; duplicate calls within 24 hours return the existing order. " +
            "orderType must be ONLINE or INSTORE. " +
            "basketJson is a JSON-serialised Basket with an items array. " +
            "shippingDataJson is a JSON-serialised ShippingData (required for ONLINE orders, null for INSTORE)."
    )
    public StartOrderResponse startOrder(
            @ToolParam(description = "The customer's profile ID") String customerId,
            @ToolParam(description = "Must be true — agent must have confirmed the customer's intent before placing the order") boolean customerConfirmed,
            @ToolParam(description = "Unique key for this order attempt to prevent duplicate orders") String idempotencyKey,
            @ToolParam(description = "The store/shop ID to place the order against") String shopId,
            @ToolParam(description = "Brief description of the order") String description,
            @ToolParam(description = "Order type: ONLINE or INSTORE") String orderType,
            @ToolParam(description = "JSON of the Basket with items array, e.g. {\"items\":[{\"name\":\"chips\",\"quantity\":2,\"price\":10,\"totalPrice\":20}]}") String basketJson,
            @ToolParam(required = false, description = "JSON of ShippingData (required for ONLINE orders). null for INSTORE orders.") String shippingDataJson) {

        // Guard 2 — consent: must be first, before any data access
        if (!customerConfirmed) {
            return StartOrderResponse.rejected(
                "Customer confirmation not recorded. Obtain explicit order confirmation before placing the order.");
        }

        // Guard 3a — idempotency key presence
        if (!StringUtils.hasText(idempotencyKey)) {
            return StartOrderResponse.rejected(
                "Idempotency key is required. Provide a unique key for this order attempt.");
        }

        // Guard 1 — identity
        if (!userProfileRepo.existsById(customerId)) {
            return StartOrderResponse.rejected(
                "No customer account found for the provided ID.");
        }

        // Guard 3b — idempotency deduplication: check for an existing order in the window
        Date windowStart = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(idempotencyWindowHours));
        Order existingOrder = orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                customerId, idempotencyKey, windowStart);
        if (existingOrder != null) {
            LOG.info("Idempotent start_order: returning existing orderId={} for customerId={} key={}",
                    existingOrder.getId(), customerId, idempotencyKey);
            return StartOrderResponse.idempotent(existingOrder);
        }

        // Construct the Order from tool parameters
        Order order;
        try {
            order = buildOrder(customerId, shopId, description, orderType, basketJson, shippingDataJson);
        } catch (Exception e) {
            LOG.warn("start_order: order construction failed — customerId={} reason={}", customerId, e.getMessage());
            return StartOrderResponse.rejected("Order details are invalid: " + sanitizeMessage(e.getMessage()));
        }

        // Stamp idempotency key before delegating to domain layer
        order.getTag().put("idempotencyKey", idempotencyKey);

        // Delegate to domain service — catch IllegalArgumentException and translate
        Order created;
        try {
            created = orderService.startOrder(order);
        } catch (IllegalArgumentException e) {
            LOG.warn("start_order: domain layer rejected order for customerId={} — {}", customerId, e.getMessage());
            return StartOrderResponse.rejected(sanitizeMessage(e.getMessage()));
        } catch (Exception e) {
            LOG.error("start_order: unexpected error for customerId={}", customerId, e);
            return StartOrderResponse.rejected("Order could not be created at this time. Please try again.");
        }

        LOG.info("start_order: created orderId={} for customerId={}", created.getId(), customerId);
        return StartOrderResponse.success(created);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order buildOrder(String customerId,
                             String shopId,
                             String description,
                             String orderType,
                             String basketJson,
                             String shippingDataJson) throws Exception {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setShopId(shopId);
        order.setDescription(description);

        OrderType type;
        try {
            type = OrderType.valueOf(orderType.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("orderType must be ONLINE or INSTORE, got: " + orderType);
        }
        order.setOrderType(type);

        if (!StringUtils.hasText(basketJson)) {
            throw new IllegalArgumentException("basketJson is required");
        }
        Basket basket = objectMapper.readValue(basketJson, Basket.class);
        order.setBasket(basket);

        if (StringUtils.hasText(shippingDataJson)) {
            ShippingData shippingData = objectMapper.readValue(shippingDataJson, ShippingData.class);
            order.setShippingData(shippingData);
        }

        return order;
    }

    /**
     * Strips internal details from exception messages before surfacing them to an AI agent.
     * Internal IDs (UUIDs / short store codes) and stack-trace fragments must not reach the
     * agent verbatim. This method keeps the readable reason phrase but removes any substring
     * that looks like a MongoDB ObjectId or UUID.
     */
    private static String sanitizeMessage(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "Order could not be created due to an invalid request.";
        }
        // Strip embedded IDs (24-char hex ObjectIds and UUID patterns) before forwarding
        String cleaned = raw
                .replaceAll("[0-9a-fA-F]{24}", "<id>")                        // Mongo ObjectId
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<id>"); // UUID
        // Truncate to a safe length to avoid leaking huge internal messages
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    // -------------------------------------------------------------------------
    // Response types
    // -------------------------------------------------------------------------

    /** Structured response returned by the {@code start_order} MCP tool. */
    public static class StartOrderResponse {
        private final boolean success;
        private final String orderId;
        private final String orderStatus;
        private final double estimatedTotal;
        private final boolean idempotent;
        private final String message;

        private StartOrderResponse(boolean success, String orderId, String orderStatus,
                                   double estimatedTotal, boolean idempotent, String message) {
            this.success = success;
            this.orderId = orderId;
            this.orderStatus = orderStatus;
            this.estimatedTotal = estimatedTotal;
            this.idempotent = idempotent;
            this.message = message;
        }

        static StartOrderResponse rejected(String reason) {
            return new StartOrderResponse(false, null, null, 0.0, false, reason);
        }

        static StartOrderResponse success(Order order) {
            return new StartOrderResponse(
                    true,
                    order.getId(),
                    order.getStage() != null ? order.getStage().name() : null,
                    order.getTotalAmount(),
                    false,
                    null);
        }

        static StartOrderResponse idempotent(Order existingOrder) {
            return new StartOrderResponse(
                    true,
                    existingOrder.getId(),
                    existingOrder.getStage() != null ? existingOrder.getStage().name() : null,
                    existingOrder.getTotalAmount(),
                    true,
                    "Duplicate request — returning existing order.");
        }

        public boolean isSuccess() { return success; }
        public String getOrderId() { return orderId; }
        public String getOrderStatus() { return orderStatus; }
        public double getEstimatedTotal() { return estimatedTotal; }
        public boolean isIdempotent() { return idempotent; }
        public String getMessage() { return message; }
    }
}
