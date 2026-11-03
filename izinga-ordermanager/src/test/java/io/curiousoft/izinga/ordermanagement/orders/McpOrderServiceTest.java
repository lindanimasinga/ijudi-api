package io.curiousoft.izinga.ordermanagement.orders;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.DayOfWeek;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link McpOrderService#startOrder} — all three SA-022-01 guards
 * and the happy-path creation flow.
 */
@RunWith(MockitoJUnitRunner.class)
public class McpOrderServiceTest {

    private McpOrderService mcpOrderService;

    @Mock
    private OrderServiceImpl orderService;
    @Mock
    private UserProfileRepo userProfileRepo;
    @Mock
    private OrderRepository orderRepository;

    // Mirror Spring Boot's production ObjectMapper behaviour: ignore unknown properties
    // (e.g. computed vals such as BasketItem.totalPrice) rather than failing on them.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final long idempotencyWindowHours = 24L;

    private static final String CUSTOMER_ID = "cust-001";
    private static final String SHOP_ID = "shop-001";
    private static final String IDEMPOTENCY_KEY = "order-key-abc123";

    @Before
    public void setUp() {
        StoreScopeContext.clear();
        mcpOrderService = new McpOrderService(
                orderService, userProfileRepo, orderRepository,
                objectMapper, idempotencyWindowHours);
    }

    @After
    public void tearDown() {
        StoreScopeContext.clear();
    }

    // -------------------------------------------------------------------------
    // Guard 2: customerConfirmed must be true
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_customerConfirmedFalse_rejectsWithoutDataAccess() {
        // when
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, false, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        // then: rejected before any repository call
        Assert.assertFalse(response.isSuccess());
        Assert.assertTrue(response.getMessage().toLowerCase().contains("confirmation"));
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    // -------------------------------------------------------------------------
    // Guard 3a: idempotency key must not be blank
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_blankIdempotencyKey_rejectsWithoutDataAccess() {
        // when
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, "  ", SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        // then: rejected before any repository call
        Assert.assertFalse(response.isSuccess());
        Assert.assertTrue(response.getMessage().toLowerCase().contains("idempotency"));
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    @Test
    public void startOrder_nullIdempotencyKey_rejectsWithoutDataAccess() {
        // when
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, null, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        // then
        Assert.assertFalse(response.isSuccess());
        verifyNoInteractions(userProfileRepo, orderRepository, orderService);
    }

    // -------------------------------------------------------------------------
    // Guard 1: customer identity must exist in UserProfile repository
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_customerNotFound_rejectsWithGenericMessage() {
        // given
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(false);

        // when
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        // then
        Assert.assertFalse(response.isSuccess());
        Assert.assertTrue(response.getMessage().toLowerCase().contains("no customer account"));
        // Domain service must never be reached
        verify(orderService, never()).startOrder(any());
    }

    // -------------------------------------------------------------------------
    // Guard 3b: idempotent duplicate — returns existing order, no new one created
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_duplicateWithinWindow_returnsExistingOrderWithoutCreatingNew() {
        // given
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        Order existingOrder = buildOrder("existing-id", OrderStage.STAGE_1_WAITING_STORE_CONFIRM, 50.0);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(existingOrder);

        // when
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "desc", "ONLINE", basketJson(), null);

        // then: success with existing order data, domain startOrder() never called
        Assert.assertTrue(response.isSuccess());
        Assert.assertTrue(response.isIdempotent());
        Assert.assertEquals("existing-id", response.getOrderId());
        Assert.assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM.name(), response.getOrderStatus());
        verify(orderService, never()).startOrder(any());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_happyPath_createsOrderAndSetsIdempotencyTag() {
        // given
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);

        Order created = buildOrder("new-order-id", OrderStage.STAGE_0_CUSTOMER_NOT_PAID, 71.75);
        when(orderService.startOrder(any(Order.class))).thenReturn(created);

        // when
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "delivery order", "ONLINE", basketJson(), shippingJson());

        // then
        Assert.assertTrue(response.isSuccess());
        Assert.assertFalse(response.isIdempotent());
        Assert.assertEquals("new-order-id", response.getOrderId());
        Assert.assertEquals(OrderStage.STAGE_0_CUSTOMER_NOT_PAID.name(), response.getOrderStatus());
        Assert.assertNull(response.getMessage());

        // idempotency key must be stamped on the order passed to domain service
        verify(orderService).startOrder(argThat(order ->
                IDEMPOTENCY_KEY.equals(order.getTag().get("idempotencyKey"))));
    }

    // -------------------------------------------------------------------------
    // Domain exception translation — no raw internal messages must leak
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_domainThrowsIllegalArgument_returnsSanitizedMessage() {
        // given
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);

        String rawInternalMessage = "shop with id abcdef1234567890abcdef12 does not exist";
        when(orderService.startOrder(any(Order.class)))
                .thenThrow(new IllegalArgumentException(rawInternalMessage));

        // when
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "delivery order", "ONLINE", basketJson(), shippingJson());

        // then: success=false but raw MongoDB ObjectId is stripped from the message
        Assert.assertFalse(response.isSuccess());
        Assert.assertNotNull(response.getMessage());
        // Raw 24-char hex ID must not appear in the response message
        Assert.assertFalse("Raw internal ID must not leak",
                response.getMessage().contains("abcdef1234567890abcdef12"));
        // The readable part of the message may remain
        Assert.assertTrue(response.getMessage().contains("does not exist"));
    }

    @Test
    public void startOrder_domainThrowsUnexpectedException_returnsGenericMessage() {
        // given
        when(userProfileRepo.existsById(CUSTOMER_ID)).thenReturn(true);
        when(orderRepository.findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), any(Date.class)))
                .thenReturn(null);
        when(orderService.startOrder(any(Order.class)))
                .thenThrow(new RuntimeException("NullPointerException at line 347"));

        // when
        McpOrderService.StartOrderResponse response = mcpOrderService.startOrder(
                CUSTOMER_ID, true, IDEMPOTENCY_KEY, SHOP_ID,
                "delivery order", "ONLINE", basketJson(), shippingJson());

        // then
        Assert.assertFalse(response.isSuccess());
        Assert.assertNotNull(response.getMessage());
        Assert.assertTrue(response.getMessage().toLowerCase().contains("could not be created"));
        verify(orderService).startOrder(any());
    }

    // -------------------------------------------------------------------------
    // FIX-01/FIX-02: CallerIdentityViolationException must pass through verbatim
    // (not replaced by the generic "could not be created" error)
    // -------------------------------------------------------------------------

    @Test
    public void startOrder_customerAudienceThirdPartyId_returnsRejectedWithIdentityRefusal() {
        // FIX-02: CUSTOMER audience + caller phone set; customerId belongs to a DIFFERENT customer.
        // The domain layer (mocked) throws CallerIdentityViolationException — the MCP layer must
        // NOT replace it with a generic error. The IDENTITY_REFUSAL prefix must be visible.
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

        // FIX-01: the response must be rejected, and the message must contain the
        // machine-parseable IDENTITY_REFUSAL prefix — NOT the generic error string.
        Assert.assertFalse(response.isSuccess());
        Assert.assertNotNull(response.getMessage());
        Assert.assertTrue(
                "IDENTITY_REFUSAL prefix must be present in rejection message",
                response.getMessage().contains("IDENTITY_REFUSAL"));
        Assert.assertFalse(
                "Generic error must not appear when an identity violation occurs",
                response.getMessage().contains("could not be created"));
    }

    @Test
    public void startOrder_storeAudience_happyPath_createsOrderSuccessfully() {
        // FIX-02 regression: STORE audience must not be affected by the new catch block.
        // A store-initiated order (placing an order on behalf of a customer) should succeed.
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

        Assert.assertTrue("STORE audience order must succeed", response.isSuccess());
        Assert.assertEquals("store-placed-order-id", response.getOrderId());
    }

    // -------------------------------------------------------------------------
    // Helper factories
    // -------------------------------------------------------------------------

    private String basketJson() {
        return "{\"items\":[{\"name\":\"chips\",\"quantity\":2,\"price\":10,\"totalPrice\":20}]}";
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
