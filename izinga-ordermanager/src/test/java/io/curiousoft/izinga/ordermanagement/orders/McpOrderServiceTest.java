package io.curiousoft.izinga.ordermanagement.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link McpOrderService#startOrder} — all three SA-022-01 guards
 * and the happy-path creation flow.
 */
@ExtendWith(MockitoExtension.class)
public class McpOrderServiceTest {

    private McpOrderService mcpOrderService;

    @Mock
    private OrderServiceImpl orderService;
    @Mock
    private UserProfileRepo userProfileRepo;
    @Mock
    private OrderRepository orderRepository;

    // Spring Boot auto-registers the Kotlin module on its managed ObjectMapper, but tests
    // create a raw ObjectMapper. Without the Kotlin module, Jackson cannot deserialize
    // Kotlin classes that lack a no-arg constructor (e.g. BasketItem). Register it here
    // so buildOrder() succeeds in unit tests exactly as it does at runtime.
    // FAIL_ON_UNKNOWN_PROPERTIES is disabled to match Spring Boot's default ObjectMapper
    // configuration and to tolerate extra JSON fields (e.g. computed Kotlin vals).
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final long idempotencyWindowHours = 24L;

    private static final String CUSTOMER_ID = "cust-001";
    private static final String SHOP_ID = "shop-001";
    private static final String IDEMPOTENCY_KEY = "order-key-abc123";

    @BeforeEach
    public void setUp() {
        StoreScopeContext.clear();
        CallerIdentityContext.setMcpRequest(false);
        mcpOrderService = new McpOrderService(
                orderService, userProfileRepo, orderRepository,
                objectMapper, idempotencyWindowHours);
    }

    @AfterEach
    public void tearDown() {
        StoreScopeContext.clear();
    }

    // -------------------------------------------------------------------------
    // Guard 2: customerConfirmed must be true
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_customerConfirmedFalse_rejectsWithoutDataAccess() {
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, false, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("confirmation"));
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    // -------------------------------------------------------------------------
    // Guard 3a: idempotency key must not be blank
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_blankIdempotencyKey_rejectsWithoutDataAccess() {
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, "  ", SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("idempotency"));
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    @Test
    public void startOrder_nullIdempotencyKey_rejectsWithoutDataAccess() {
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, null, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    // -------------------------------------------------------------------------
    // Guard 1: customer identity must exist in UserProfile repository
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_customerNotFound_rejectsWithGenericMessage() {
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(false);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("no customer account"));
        verify(orderService, never()).startOrder(any());
    }

    // -------------------------------------------------------------------------
    // Guard 3b: idempotent duplicate — returns existing order, no new one created
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_duplicateWithinWindow_returnsExistingOrderWithoutCreatingNew() {
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        Order existingOrder = buildOrder("existing-id", OrderStage.STAGE_1_WAITING_STORE_CONFIRM, 50.0);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(existingOrder);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertTrue(response.isSuccess());
        assertTrue(response.isIdempotent());
        assertEquals("existing-id", response.getOrderId());
        assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM.name(), response.getOrderStatus());
        verify(orderService, never()).startOrder(any());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_happyPath_createsOrderAndSetsIdempotencyTag() {
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);

        Order created = buildOrder("new-order-id", OrderStage.STAGE_0_CUSTOMER_NOT_PAID, 71.75);
        when(orderService.startOrder(any(Order.class))).thenReturn(created);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "delivery order", "ONLINE", basketJson(), shippingJson());

        assertTrue(response.isSuccess());
        assertFalse(response.isIdempotent());
        assertEquals("new-order-id", response.getOrderId());
        assertEquals(OrderStage.STAGE_0_CUSTOMER_NOT_PAID.name(), response.getOrderStatus());
        assertNull(response.getMessage());

        verify(orderService).startOrder(argThat(order ->
                IDEMPOTENCY_KEY.equals(order.getTag().get("idempotencyKey"))));
    }

    // -------------------------------------------------------------------------
    // Domain exception translation — no raw internal messages must leak
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_domainThrowsIllegalArgument_returnsSanitizedMessage() {
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);

        String rawInternalMessage = "shop with id abcdef1234567890abcdef12 does not exist";
        when(orderService.startOrder(any(Order.class)))
                .thenThrow(new IllegalArgumentException(rawInternalMessage));

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "delivery order", "ONLINE", basketJson(), shippingJson());

        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().contains("abcdef1234567890abcdef12"),
                "Raw internal ID must not leak");
        assertTrue(response.getMessage().contains("does not exist"));
    }

    @Test
    public void startOrder_domainThrowsUnexpectedException_returnsGenericMessage() {
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);
        when(orderService.startOrder(any(Order.class)))
                .thenThrow(new RuntimeException("NullPointerException at line 347"));

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "delivery order", "ONLINE", basketJson(), shippingJson());

        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().toLowerCase().contains("could not be created"));
        verify(orderService).startOrder(any());
    }

    // -------------------------------------------------------------------------
    // FIX-01/FIX-02: CallerIdentityViolationException must pass through verbatim
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_customerAudienceThirdPartyId_returnsRejectedWithIdentityRefusal() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone("+27801112222");

        String thirdPartyId = "other-customer-id";
        when(userProfileRepo.existsById(thirdPartyId)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(thirdPartyId), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);
        when(orderService.startOrder(any(Order.class)))
                .thenThrow(new CallerIdentityViolationException(
                        "IDENTITY_REFUSAL: I can only assist with queries for your own account. " +
                        "For privacy and security reasons, I am not authorised to access another person's information. " +
                        "Please contact iZinga support if you need further help."));

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                thirdPartyId, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("IDENTITY_REFUSAL"),
                "IDENTITY_REFUSAL prefix must be present in rejection message");
        assertFalse(response.getMessage().contains("could not be created"),
                "Generic error must not appear when an identity violation occurs");
    }

    // -------------------------------------------------------------------------
    // SEC-MCP-01 / CORRECTION 3: audience gate on start_order
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_mcpNullAudience_returnsRejectedWithIdentityRefusal() {
        CallerIdentityContext.setMcpRequest(true);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("IDENTITY_REFUSAL"),
                "Response must contain IDENTITY_REFUSAL");
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    @Test
    public void startOrder_mcpDriverAudience_returnsRejectedWithIdentityRefusal() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.DRIVER);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("IDENTITY_REFUSAL"),
                "Response must contain IDENTITY_REFUSAL");
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    @Test
    public void startOrder_mcpCustomerAudience_proceedsToGuards() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, false, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("confirmation"),
                "Should reach consent guard, not audience gate");
    }

    @Test
    public void startOrder_nonMcpNullAudience_proceedsNormally() {
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, false, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("confirmation"),
                "Must reach consent guard, not audience gate");
    }

    @Test
    public void startOrder_storeAudience_happyPath_createsOrderSuccessfully() {
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setCallerPhone("+27712345678");

        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);
        Order created = buildOrder("store-placed-order-id", OrderStage.STAGE_0_CUSTOMER_NOT_PAID, 50.0);
        when(orderService.startOrder(any(Order.class))).thenReturn(created);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertTrue(response.isSuccess(), "STORE audience order must succeed");
        assertEquals("store-placed-order-id", response.getOrderId());
    }

    /**
     * AMD-01 (REQ-A9): ADMIN audience must be permitted to create orders.
     * Follows the same happy-path pattern as storeAudience_happyPath_createsOrderSuccessfully.
     */
    @Test
    public void startOrder_adminAudience_passes() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);
        Order created = buildOrder("admin-placed-order-id", OrderStage.STAGE_0_CUSTOMER_NOT_PAID, 50.0);
        when(orderService.startOrder(any(Order.class))).thenReturn(created);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertTrue(response.isSuccess(), "ADMIN audience order must succeed");
        assertEquals("admin-placed-order-id", response.getOrderId());
    }

    /**
     * AMD-01 regression: DRIVER must still be rejected even after ADMIN was added.
     */
    @Test
    public void startOrder_driverAudience_rejected_unchanged() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.DRIVER);

        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("IDENTITY_REFUSAL"),
                "DRIVER audience must still be rejected with IDENTITY_REFUSAL");
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    // -------------------------------------------------------------------------
    // Helper factories
    // -------------------------------------------------------------------------

    private String basketJson() {
        // BasketItem constructor params are: name, quantity, price, discountPerc.
        // totalPrice is a computed read-only val (price*quantity) — it cannot be used
        // as a constructor param and must not appear in deserialization payloads.
        return "{\"items\":[{\"name\":\"chips\",\"quantity\":2,\"price\":10,\"discountPerc\":0}]}";
    }

    private String shippingJson() {
        return "{\"fromAddress\":\"1 Sender St\",\"toAddress\":\"2 Dest Rd\",\"type\":\"SCHEDULED_DELIVERY\"}";
    }

    private Order buildOrder(String id, OrderStage stage, double serviceFee) {
        Order order = new Order();
        order.setId(id);
        order.setStage(stage);
        order.setServiceFee(serviceFee);
        order.setCustomerId(CUSTOMER_ID);
        order.setShopId(SHOP_ID);
        order.setDescription("test order");
        order.setOrderType(OrderType.ONLINE);

        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        basket.setItems(items);
        order.setBasket(basket);

        ShippingData shipping = new ShippingData("1 Sender St", "2 Dest Rd",
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        order.setShippingData(shipping);

        return order;
    }
}
