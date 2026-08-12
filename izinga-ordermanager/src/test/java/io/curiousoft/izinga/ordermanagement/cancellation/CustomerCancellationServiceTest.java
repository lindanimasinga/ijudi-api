package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.Basket;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.OrderStage;
import io.curiousoft.izinga.commons.model.PaymentType;
import io.curiousoft.izinga.commons.order.CancellationPreviewResponse;
import io.curiousoft.izinga.commons.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerCancellationServiceTest {

    private static final String HMAC_SECRET = "test-hmac-secret-unit-test";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CancellationFeeCalculationService feeCalculationService;
    @Mock
    private CancellationPolicyCacheService policyCache;
    @Mock
    private CancellationAuditService auditService;
    @Mock
    private CancellationPaymentHandler paymentHandler;

    private CustomerCancellationService sut;

    @BeforeEach
    void setUp() {
        sut = new CustomerCancellationService(
                orderRepository,
                feeCalculationService,
                policyCache,
                auditService,
                paymentHandler,
                HMAC_SECRET);
    }

    // --- helpers ---

    /** Creates a fully formed cancelable order with an initialised basket. */
    private Order cancelableOrder(String id, OrderStage stage) {
        Order o = new Order();
        o.setId(id);
        o.setStage(stage);
        o.setCustomerId("cust-1");
        o.setPaymentType(PaymentType.YOCO);
        o.setCreatedDate(Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        o.setBasket(new Basket()); // basket is lateinit; must be set
        return o;
    }

    private CancellationPolicy defaultPolicy() {
        CancellationPolicy p = new CancellationPolicy();
        p.setFeeTokenValidityMinutes(15);
        p.setFeeTokenTolerance(0.01);
        return p;
    }

    private CancellationFeeResult feeResult(double fee, OrderStage stage) {
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("noticePeriodMinutes", 30L);
        return new CancellationFeeResult(fee, factors, stage);
    }

    private CancellationPaymentResult paymentResult() {
        return new CancellationPaymentResult(
                CancellationPaymentResult.Status.PENDING_MANUAL_RECONCILIATION,
                ManualReconciliationPaymentHandler.HANDLER_NAME, "notes");
    }

    // =========================================================================
    // PREVIEW tests
    // =========================================================================

    @Test
    void previewCancellationFee_happyPath_returnsResponse() {
        Order order = cancelableOrder("ord-1", OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(policyCache.getPolicy()).thenReturn(defaultPolicy());
        when(feeCalculationService.calculate(eq(order), any(Instant.class)))
                .thenReturn(feeResult(0.0, OrderStage.STAGE_1_WAITING_STORE_CONFIRM));

        CancellationPreviewResponse response = sut.previewCancellationFee("ord-1");

        assertNotNull(response);
        assertEquals("ord-1", response.getOrderId());
        assertEquals(0.0, response.getCalculatedFeeZAR(), 0.0);
        assertNotNull(response.getFeeToken());
        assertNotNull(response.getTokenExpiresAt());
        assertTrue(response.getTokenExpiresAt().after(new Date()));
        verify(orderRepository, times(1)).findById("ord-1");
        verify(feeCalculationService, times(1)).calculate(eq(order), any(Instant.class));
        // Must NOT save, change stage, or fire notifications
        verify(orderRepository, never()).save(any());
    }

    @Test
    void previewCancellationFee_withNonZeroFee_tokenContainsFee() {
        Order order = cancelableOrder("ord-2", OrderStage.STAGE_2_STORE_PROCESSING);
        when(orderRepository.findById("ord-2")).thenReturn(Optional.of(order));
        when(policyCache.getPolicy()).thenReturn(defaultPolicy());
        when(feeCalculationService.calculate(eq(order), any(Instant.class)))
                .thenReturn(feeResult(50.0, OrderStage.STAGE_2_STORE_PROCESSING));

        CancellationPreviewResponse response = sut.previewCancellationFee("ord-2");

        assertEquals(50.0, response.getCalculatedFeeZAR(), 0.0001);
        // Token must encode 50.00 ZAR → 5000 cents
        assertTrue(response.getFeeToken().contains("5000"),
                "Token must contain cents representation");
    }

    @Test
    void previewCancellationFee_orderNotFound_throws() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> sut.previewCancellationFee("missing"));
    }

    @Test
    void previewCancellationFee_alreadyCancelled_throws() {
        Order order = cancelableOrder("ord-3", OrderStage.CANCELLED);
        when(orderRepository.findById("ord-3")).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class, () -> sut.previewCancellationFee("ord-3"));
    }

    @Test
    void previewCancellationFee_alreadyCompleted_throws() {
        Order order = cancelableOrder("ord-4", OrderStage.STAGE_7_ALL_PAID);
        when(orderRepository.findById("ord-4")).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class, () -> sut.previewCancellationFee("ord-4"));
    }

    // =========================================================================
    // CONFIRM tests
    // =========================================================================

    @Test
    void confirmCustomerCancelOrder_happyPath_cancelsAndPersists() {
        Order order = cancelableOrder("ord-5", OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        when(orderRepository.findById("ord-5")).thenReturn(Optional.of(order));
        when(policyCache.getPolicy()).thenReturn(defaultPolicy());
        when(feeCalculationService.calculate(any(Order.class)))
                .thenReturn(feeResult(0.0, OrderStage.STAGE_1_WAITING_STORE_CONFIRM));
        when(paymentHandler.handle(any(), anyDouble(), anyDouble())).thenReturn(paymentResult());

        CancellationAuditLog auditLog = new CancellationAuditLog();
        auditLog.setId("audit-001");
        when(auditService.insert(any(), any(), anyDouble(), anyDouble(),
                any(), any(), any(), any())).thenReturn(auditLog);

        Order saved = new Order();
        saved.setId("ord-5");
        saved.setStage(OrderStage.CANCELLED);
        saved.setBasket(new Basket());
        when(orderRepository.save(any())).thenReturn(saved);

        String token = sut.buildToken("ord-5", 0.0, Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond());

        Order result = sut.confirmCustomerCancelOrder("ord-5", token);

        assertEquals(OrderStage.CANCELLED, result.getStage());
        // Audit MUST be written before save
        InOrder inOrder = inOrder(auditService, orderRepository);
        inOrder.verify(auditService).insert(any(), any(), anyDouble(), anyDouble(),
                any(), eq(token), any(), any());
        inOrder.verify(orderRepository).save(any());
    }

    @Test
    void confirmCustomerCancelOrder_withNonZeroFee_succeeds() {
        Order order = cancelableOrder("ord-6", OrderStage.STAGE_2_STORE_PROCESSING);
        when(orderRepository.findById("ord-6")).thenReturn(Optional.of(order));
        when(policyCache.getPolicy()).thenReturn(defaultPolicy());
        when(feeCalculationService.calculate(any(Order.class)))
                .thenReturn(feeResult(50.0, OrderStage.STAGE_2_STORE_PROCESSING));
        when(paymentHandler.handle(any(), anyDouble(), anyDouble())).thenReturn(paymentResult());

        CancellationAuditLog auditLog = new CancellationAuditLog();
        auditLog.setId("audit-002");
        when(auditService.insert(any(), any(), anyDouble(), anyDouble(),
                any(), any(), any(), any())).thenReturn(auditLog);

        Order saved = new Order();
        saved.setId("ord-6");
        saved.setStage(OrderStage.CANCELLED);
        saved.setBasket(new Basket());
        when(orderRepository.save(any())).thenReturn(saved);

        // Build token encoding 50.00 ZAR (5000 cents)
        String token = sut.buildToken("ord-6", 50.0, Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond());

        Order result = sut.confirmCustomerCancelOrder("ord-6", token);

        assertNotNull(result);
        verify(auditService).insert(any(), any(), eq(50.0), anyDouble(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // NO path succeeds without feeToken (including R0 case)
    // -------------------------------------------------------------------------

    @Test
    void confirmCustomerCancelOrder_nullToken_throws() {
        // null/blank check is the first guard — loadOrder is never reached,
        // so no repository stub is needed
        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-7", null));
    }

    @Test
    void confirmCustomerCancelOrder_emptyToken_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-8", "   "));
    }

    // -------------------------------------------------------------------------
    // Token validation — wrong orderId (throws before reaching fee calculation)
    // -------------------------------------------------------------------------

    @Test
    void confirmCustomerCancelOrder_tokenForDifferentOrder_throws() {
        Order order = cancelableOrder("ord-9", OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        when(orderRepository.findById("ord-9")).thenReturn(Optional.of(order));
        // No stubs for policyCache/feeCalculationService — orderId mismatch throws first

        String wrongToken = sut.buildToken("different-order", 0.0,
                Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond());
        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-9", wrongToken));
    }

    // -------------------------------------------------------------------------
    // Token validation — expired (throws before reaching fee calculation)
    // -------------------------------------------------------------------------

    @Test
    void confirmCustomerCancelOrder_expiredToken_throws() {
        Order order = cancelableOrder("ord-10", OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        when(orderRepository.findById("ord-10")).thenReturn(Optional.of(order));
        // No stubs for policyCache/feeCalculationService — expiry check throws first

        String expiredToken = sut.buildToken("ord-10", 0.0,
                Instant.now().minus(1, ChronoUnit.SECONDS).getEpochSecond());
        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-10", expiredToken));
    }

    // -------------------------------------------------------------------------
    // Token validation — tampered HMAC (throws before reaching fee calculation)
    // -------------------------------------------------------------------------

    @Test
    void confirmCustomerCancelOrder_tamperedToken_throws() {
        Order order = cancelableOrder("ord-11", OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        when(orderRepository.findById("ord-11")).thenReturn(Optional.of(order));
        // No stubs for policyCache/feeCalculationService — HMAC check throws first

        String validToken = sut.buildToken("ord-11", 0.0,
                Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond());
        String tampered = validToken.substring(0, validToken.lastIndexOf('|') + 1) + "BADHMAC";
        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-11", tampered));
    }

    // -------------------------------------------------------------------------
    // Token validation — malformed (wrong segment count; throws before fee calculation)
    // -------------------------------------------------------------------------

    @Test
    void confirmCustomerCancelOrder_malformedToken_throws() {
        Order order = cancelableOrder("ord-12", OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        when(orderRepository.findById("ord-12")).thenReturn(Optional.of(order));
        // No stubs for policyCache/feeCalculationService — format check throws first

        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-12", "not-a-valid-token"));
    }

    // -------------------------------------------------------------------------
    // Token validation — fee drift exceeds tolerance
    // -------------------------------------------------------------------------

    @Test
    void confirmCustomerCancelOrder_feeDriftBeyondTolerance_throws() {
        Order order = cancelableOrder("ord-13", OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        when(orderRepository.findById("ord-13")).thenReturn(Optional.of(order));
        when(policyCache.getPolicy()).thenReturn(defaultPolicy());
        // Token says 0.0 ZAR, but recalculation returns 50.0 ZAR → drift >> tolerance
        when(feeCalculationService.calculate(any(Order.class)))
                .thenReturn(feeResult(50.0, OrderStage.STAGE_1_WAITING_STORE_CONFIRM));

        String token = sut.buildToken("ord-13", 0.0,
                Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond());
        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-13", token));
    }

    // -------------------------------------------------------------------------
    // Audit failure aborts cancellation (no order save)
    // -------------------------------------------------------------------------

    @Test
    void confirmCustomerCancelOrder_auditWriteFails_abortsWithoutSavingOrder() {
        Order order = cancelableOrder("ord-14", OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        when(orderRepository.findById("ord-14")).thenReturn(Optional.of(order));
        when(policyCache.getPolicy()).thenReturn(defaultPolicy());
        when(feeCalculationService.calculate(any(Order.class)))
                .thenReturn(feeResult(0.0, OrderStage.STAGE_1_WAITING_STORE_CONFIRM));
        when(paymentHandler.handle(any(), anyDouble(), anyDouble())).thenReturn(paymentResult());
        when(auditService.insert(any(), any(), anyDouble(), anyDouble(),
                any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Mongo is down"));

        String token = sut.buildToken("ord-14", 0.0,
                Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond());

        // The service wraps the repository exception in IllegalStateException
        assertThrows(IllegalStateException.class,
                () -> sut.confirmCustomerCancelOrder("ord-14", token));

        // Order must NOT have been saved
        verify(orderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Already cancelled or completed
    // -------------------------------------------------------------------------

    @Test
    void confirmCustomerCancelOrder_alreadyCancelled_throws() {
        Order order = cancelableOrder("ord-15", OrderStage.CANCELLED);
        when(orderRepository.findById("ord-15")).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-15", "any-token"));
    }

    @Test
    void confirmCustomerCancelOrder_alreadyCompleted_throws() {
        Order order = cancelableOrder("ord-16", OrderStage.STAGE_7_ALL_PAID);
        when(orderRepository.findById("ord-16")).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class,
                () -> sut.confirmCustomerCancelOrder("ord-16", "any-token"));
    }

    // -------------------------------------------------------------------------
    // buildToken / computeHmac internals
    // -------------------------------------------------------------------------

    @Test
    void buildToken_deterministic() {
        String t1 = sut.buildToken("ord-x", 25.0, 1234567890L);
        String t2 = sut.buildToken("ord-x", 25.0, 1234567890L);
        assertEquals(t1, t2);
    }

    @Test
    void buildToken_differentInputsDifferentTokens() {
        String t1 = sut.buildToken("ord-x", 25.0, 1234567890L);
        String t2 = sut.buildToken("ord-x", 26.0, 1234567890L);
        assertNotEquals(t1, t2);
    }

    @Test
    void buildToken_containsCorrectCentsEncoding() {
        // 50.00 ZAR → 5000 cents
        String token = sut.buildToken("ord-x", 50.0, 999999L);
        String[] parts = token.split("\\|");
        assertEquals(4, parts.length);
        assertEquals("ord-x", parts[0]);
        assertEquals("5000", parts[1]);
        assertEquals("999999", parts[2]);
    }
}
