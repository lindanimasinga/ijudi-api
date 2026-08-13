package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.OrderStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationFeeCalculationServiceTest {

    @Mock
    private CancellationPolicyCacheService policyCache;

    private CancellationFeeCalculationService sut;

    @BeforeEach
    void setUp() {
        sut = new CancellationFeeCalculationService(policyCache);
    }

    // --- helpers ---

    private CancellationPolicy defaultZeroPolicy() {
        CancellationPolicy p = new CancellationPolicy();
        p.setNoticePeriodThresholdsMinutes(new int[0]);
        p.setNoticePeriodPenaltiesZAR(new double[0]);
        p.setStageCeilings(new HashMap<>());
        p.setIndustryRateFloor(0.0);
        p.setReBookDefaultAvailable(false);
        return p;
    }

    private Order orderWithStage(OrderStage stage) {
        Order o = new Order();
        o.setStage(stage);
        o.setCreatedDate(Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        return o;
    }

    // -------------------------------------------------------------------------
    // Happy path: all-zero policy → R0
    // -------------------------------------------------------------------------

    @Test
    void calculate_allZeroPolicy_returnsFeeZero() {
        when(policyCache.getPolicy()).thenReturn(defaultZeroPolicy());
        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertEquals(0.0, result.getCalculatedFeeZAR(), 0.0001);
        assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM, result.getAllocationStageAtCalculation());
        assertNotNull(result.getFactorBreakdown());
        verify(policyCache, times(1)).getPolicy();
    }

    // -------------------------------------------------------------------------
    // R0 is valid — not exceptional — result must always be returned
    // -------------------------------------------------------------------------

    @Test
    void calculate_zeroFee_doesNotThrow() {
        when(policyCache.getPolicy()).thenReturn(defaultZeroPolicy());
        Order order = orderWithStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertNotNull(result);
        assertEquals(0.0, result.getCalculatedFeeZAR(), 0.0);
    }

    // -------------------------------------------------------------------------
    // Notice period penalty applies when notice is within threshold
    // -------------------------------------------------------------------------

    @Test
    void calculate_withinNoticePeriodThreshold_appliesPenalty() {
        CancellationPolicy policy = defaultZeroPolicy();
        policy.setNoticePeriodThresholdsMinutes(new int[]{60, 120});
        policy.setNoticePeriodPenaltiesZAR(new double[]{50.0, 25.0});
        Map<String, Double> ceilings = new HashMap<>();
        ceilings.put(OrderStage.STAGE_1_WAITING_STORE_CONFIRM.name(), 100.0);
        policy.setStageCeilings(ceilings);
        when(policyCache.getPolicy()).thenReturn(policy);

        // Order created 30 minutes ago → notice period = 30 min → below threshold[0]=60 → penalty[0]=50
        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setCreatedDate(Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)));

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertEquals(50.0, result.getCalculatedFeeZAR(), 0.01);
    }

    // -------------------------------------------------------------------------
    // Notice period beyond all thresholds → no penalty
    // -------------------------------------------------------------------------

    @Test
    void calculate_beyondAllThresholds_noPenalty() {
        CancellationPolicy policy = defaultZeroPolicy();
        policy.setNoticePeriodThresholdsMinutes(new int[]{60});
        policy.setNoticePeriodPenaltiesZAR(new double[]{50.0});
        Map<String, Double> ceilings = new HashMap<>();
        ceilings.put(OrderStage.STAGE_2_STORE_PROCESSING.name(), 100.0);
        policy.setStageCeilings(ceilings);
        when(policyCache.getPolicy()).thenReturn(policy);

        // Order created 90 minutes ago → notice = 90 min → exceeds threshold 60 → no penalty
        Order order = orderWithStage(OrderStage.STAGE_2_STORE_PROCESSING);
        order.setCreatedDate(Date.from(Instant.now().minus(90, ChronoUnit.MINUTES)));

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertEquals(0.0, result.getCalculatedFeeZAR(), 0.0001);
    }

    // -------------------------------------------------------------------------
    // Stage ceiling is enforced — fee may not exceed ceiling
    // -------------------------------------------------------------------------

    @Test
    void calculate_feeCappedByStageCeiling() {
        CancellationPolicy policy = defaultZeroPolicy();
        policy.setNoticePeriodThresholdsMinutes(new int[]{60});
        policy.setNoticePeriodPenaltiesZAR(new double[]{200.0});
        Map<String, Double> ceilings = new HashMap<>();
        ceilings.put(OrderStage.STAGE_1_WAITING_STORE_CONFIRM.name(), 75.0);
        policy.setStageCeilings(ceilings);
        when(policyCache.getPolicy()).thenReturn(policy);

        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setCreatedDate(Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)));

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertEquals(75.0, result.getCalculatedFeeZAR(), 0.01);
    }

    // -------------------------------------------------------------------------
    // Missing stage ceiling key → ceiling treated as 0
    // -------------------------------------------------------------------------

    @Test
    void calculate_missingCeilingForStage_treatedAsZero() {
        CancellationPolicy policy = defaultZeroPolicy();
        policy.setNoticePeriodThresholdsMinutes(new int[]{60});
        policy.setNoticePeriodPenaltiesZAR(new double[]{100.0});
        policy.setStageCeilings(new HashMap<>());
        when(policyCache.getPolicy()).thenReturn(policy);

        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setCreatedDate(Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)));

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertEquals(0.0, result.getCalculatedFeeZAR(), 0.0001);
    }

    // -------------------------------------------------------------------------
    // Policy missing → IllegalStateException (NOT NPE)
    // -------------------------------------------------------------------------

    @Test
    void calculate_missingPolicy_throwsIllegalStateException() {
        when(policyCache.getPolicy()).thenThrow(
                new IllegalStateException("No cancellation_policy document found."));
        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);

        assertThrows(IllegalStateException.class, () -> sut.calculate(order, Instant.now()));
    }

    // -------------------------------------------------------------------------
    // Very recent order (near-zero notice period) → falls within first threshold → penalty applied
    // -------------------------------------------------------------------------

    @Test
    void calculate_veryRecentOrder_noticePeriodZero_appliesPenalty() {
        CancellationPolicy policy = defaultZeroPolicy();
        policy.setNoticePeriodThresholdsMinutes(new int[]{60});
        policy.setNoticePeriodPenaltiesZAR(new double[]{50.0});
        Map<String, Double> ceilings = new HashMap<>();
        ceilings.put(OrderStage.STAGE_1_WAITING_STORE_CONFIRM.name(), 100.0);
        policy.setStageCeilings(ceilings);
        when(policyCache.getPolicy()).thenReturn(policy);

        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        // Created NOW → notice period effectively 0 → 0 < 60 → penalty[0] = 50.0
        order.setCreatedDate(new Date());

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertEquals(50.0, result.getCalculatedFeeZAR(), 0.01);
    }

    // -------------------------------------------------------------------------
    // Null stage — should not throw, returns 0 fee
    // -------------------------------------------------------------------------

    @Test
    void calculate_nullStage_returnsZeroFee() {
        when(policyCache.getPolicy()).thenReturn(defaultZeroPolicy());
        Order order = new Order();
        order.setStage(null);
        order.setCreatedDate(new Date());

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertEquals(0.0, result.getCalculatedFeeZAR(), 0.0);
        assertNull(result.getAllocationStageAtCalculation());
    }

    // -------------------------------------------------------------------------
    // netRefundZAR convenience
    // -------------------------------------------------------------------------

    @Test
    void feeResult_netRefundZAR_computedCorrectly() {
        when(policyCache.getPolicy()).thenReturn(defaultZeroPolicy());
        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        double netRefund = result.netRefundZAR(100.0);
        assertEquals(100.0, netRefund, 0.0001);
    }

    @Test
    void feeResult_netRefundZAR_neverNegative() {
        CancellationPolicy policy = defaultZeroPolicy();
        policy.setNoticePeriodThresholdsMinutes(new int[]{60});
        policy.setNoticePeriodPenaltiesZAR(new double[]{999.0});
        Map<String, Double> ceilings = new HashMap<>();
        ceilings.put(OrderStage.STAGE_1_WAITING_STORE_CONFIRM.name(), 999.0);
        policy.setStageCeilings(ceilings);
        when(policyCache.getPolicy()).thenReturn(policy);

        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setCreatedDate(Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)));

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        double netRefund = result.netRefundZAR(0.0);
        assertTrue(netRefund >= 0.0, "Net refund must never be negative");
    }

    // -------------------------------------------------------------------------
    // roundToTwoDecimals helper
    // -------------------------------------------------------------------------

    @Test
    void roundToTwoDecimals_roundsCorrectly() {
        assertEquals(10.55, CancellationFeeCalculationService.roundToTwoDecimals(10.554), 0.0001);
        assertEquals(10.56, CancellationFeeCalculationService.roundToTwoDecimals(10.555), 0.0001);
        assertEquals(0.0,  CancellationFeeCalculationService.roundToTwoDecimals(0.0),   0.0001);
    }

    // -------------------------------------------------------------------------
    // QA gap 1: null createdDate → calculateNoticePeriodMinutes returns 0L (line 98)
    // Simulates a MongoDB document that was written without a createdDate field.
    // The Kotlin setter enforces non-null, so we bypass it via reflection.
    // -------------------------------------------------------------------------

    @Test
    void calculate_nullCreatedDate_noticePeriodIsZero_penaltyApplied() throws Exception {
        CancellationPolicy policy = defaultZeroPolicy();
        policy.setNoticePeriodThresholdsMinutes(new int[]{60});
        policy.setNoticePeriodPenaltiesZAR(new double[]{50.0});
        Map<String, Double> ceilings = new HashMap<>();
        ceilings.put(OrderStage.STAGE_1_WAITING_STORE_CONFIRM.name(), 100.0);
        policy.setStageCeilings(ceilings);
        when(policyCache.getPolicy()).thenReturn(policy);

        Order order = new Order();
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        // Bypass Kotlin non-null setter to simulate a MongoDB doc with no createdDate field
        Field createdDateField = order.getClass().getSuperclass().getDeclaredField("createdDate");
        createdDateField.setAccessible(true);
        createdDateField.set(order, null);

        // calculateNoticePeriodMinutes receives null → returns 0L → 0 < 60 → penalty[0] = 50.0
        CancellationFeeResult result = sut.calculate(order, Instant.now());

        assertEquals(50.0, result.getCalculatedFeeZAR(), 0.01);
    }

    // -------------------------------------------------------------------------
    // QA gap 2: null thresholds array → null check at line 108 returns 0.0
    // -------------------------------------------------------------------------

    @Test
    void calculate_nullThresholds_returnsZeroPenalty() {
        CancellationPolicy policy = new CancellationPolicy();
        policy.setNoticePeriodThresholdsMinutes(null);  // explicitly null — not empty
        policy.setNoticePeriodPenaltiesZAR(new double[]{50.0});
        policy.setStageCeilings(new HashMap<>());
        policy.setIndustryRateFloor(0.0);
        when(policyCache.getPolicy()).thenReturn(policy);

        Order order = orderWithStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        // thresholds == null → early return 0.0 → no penalty regardless of notice period
        assertEquals(0.0, result.getCalculatedFeeZAR(), 0.0001);
    }

    // -------------------------------------------------------------------------
    // QA gap 3: mismatched array lengths — i >= penalties.length path (line 114)
    // thresholds has entries but penalties is shorter (empty) → (i < penalties.length) == false
    // -------------------------------------------------------------------------

    @Test
    void calculate_mismatchedArrayLengths_returnsZeroForExceededPenalties() {
        CancellationPolicy policy = new CancellationPolicy();
        // thresholds length=1, penalties length=0 → mismatch
        policy.setNoticePeriodThresholdsMinutes(new int[]{60});
        policy.setNoticePeriodPenaltiesZAR(new double[0]);  // empty, not null (null case is gap 2)
        Map<String, Double> ceilings = new HashMap<>();
        ceilings.put(OrderStage.STAGE_1_WAITING_STORE_CONFIRM.name(), 100.0);
        policy.setStageCeilings(ceilings);
        policy.setIndustryRateFloor(0.0);
        when(policyCache.getPolicy()).thenReturn(policy);

        Order order = new Order();
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setCreatedDate(new Date()); // very recent → notice ≈ 0 < 60 → enters body at i=0

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        // i=0, penalties.length=0 → i >= penalties.length → return 0.0; raw 0.0 capped at 100.0 → 0.0
        assertEquals(0.0, result.getCalculatedFeeZAR(), 0.0001);
    }

    // -------------------------------------------------------------------------
    // QA gap 4: null getStageCeilings() map → null-ceilings branch at line 141
    // (null-stage is already covered; this covers the null-map path specifically)
    // -------------------------------------------------------------------------

    @Test
    void calculate_nullStageCeilings_treatedAsZeroCeiling() {
        CancellationPolicy policy = new CancellationPolicy();
        policy.setNoticePeriodThresholdsMinutes(new int[]{60});
        policy.setNoticePeriodPenaltiesZAR(new double[]{100.0});
        policy.setStageCeilings(null);   // null map → ceiling = 0.0 regardless of stage
        policy.setIndustryRateFloor(0.0);
        policy.setReBookDefaultAvailable(false);
        when(policyCache.getPolicy()).thenReturn(policy);

        Order order = new Order();
        order.setStage(OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
        order.setCreatedDate(new Date()); // very recent → notice ≈ 0 < 60 → penalty = 100.0

        CancellationFeeResult result = sut.calculate(order, Instant.now());

        // rawFee = 100.0; getStageCeilings() == null → stageCeiling = 0.0 → cappedFee = 0.0
        assertEquals(0.0, result.getCalculatedFeeZAR(), 0.0001);
    }
}
