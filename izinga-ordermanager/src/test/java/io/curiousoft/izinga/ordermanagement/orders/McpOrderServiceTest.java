package io.curiousoft.izinga.ordermanagement.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long idempotencyWindowHours = 24L;

    private static final String CUSTOMER_ID = "cust-001";
    private static final String SHOP_ID = "shop-001";
    private static final String IDEMPOTENCY_KEY = "order-key-abc123";

    @Before
    public void setUp() {
        mcpOrderService = new McpOrderService(
                orderService, userProfileRepo, orderRepository,
                objectMapper, idempotencyWindowHours);
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
