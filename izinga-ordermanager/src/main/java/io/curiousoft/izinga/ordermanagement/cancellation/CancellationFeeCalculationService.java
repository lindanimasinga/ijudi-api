package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.OrderStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ADR-019: Calculates the cancellation fee from five policy-driven factors.
 * <p>
 * All factor weights are read from {@link CancellationPolicy} (never hardcoded), so the
 * business team can update values without a redeploy.  With all-zero defaults in the seeded
 * policy the fee will always be R0 — that is intentional and fully supported.
 * <p>
 * The result is capped by the stage ceiling defined in the policy before being returned.
 * R0 is a valid, non-exceptional result and must be handled normally by all callers.
 */
@Service
public class CancellationFeeCalculationService {

    private static final Logger LOG = LoggerFactory.getLogger(CancellationFeeCalculationService.class);

    private final CancellationPolicyCacheService policyCache;

    public CancellationFeeCalculationService(CancellationPolicyCacheService policyCache) {
        this.policyCache = policyCache;
    }

    /**
     * Calculates the cancellation fee for a persisted order.
     *
     * @param order the persisted order (not the request object)
     * @param now   the moment of calculation (passed in so tests can control time)
     * @return fee result including the ZAR amount (possibly R0), factor breakdown, and stage
     * @throws IllegalStateException if no policy document has been seeded
     */
    public CancellationFeeResult calculate(Order order, Instant now) {
        CancellationPolicy policy = policyCache.getPolicy();

        OrderStage stage = order.getStage();
        Date createdDate = order.getCreatedDate();

        // Factor 1: notice period (minutes between order creation and cancellation request)
        long noticePeriodMinutes = calculateNoticePeriodMinutes(createdDate, now);
        double noticePenalty = resolveNoticePenalty(policy, noticePeriodMinutes);

        // Factor 2: costs incurred — estimated from allocation stage
        double costsIncurred = resolveCostsIncurred(stage, order);

        // Factor 3: industry rate floor
        double industryFloor = policy.getIndustryRateFloor();

        // Factor 4: rebook availability reduces fee when available
        boolean reBookAvailable = policy.isReBookDefaultAvailable();
        double reBookDiscount = reBookAvailable ? industryFloor * 0.5 : 0.0;

        // Factor 5: stage-based ceiling — fee may never exceed this
        double stageCeiling = resolveStateCeiling(policy, stage);

        // Raw fee before ceiling
        double rawFee = Math.max(0.0, noticePenalty + costsIncurred - reBookDiscount);
        // Apply ceiling
        double cappedFee = Math.min(rawFee, stageCeiling);
        // Round to 2 decimal places (ZAR cents)
        double finalFee = roundToTwoDecimals(cappedFee);

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("noticePeriodMinutes", noticePeriodMinutes);
        factors.put("noticePenaltyZAR", noticePenalty);
        factors.put("costsIncurredZAR", costsIncurred);
        factors.put("industryRateFloorZAR", industryFloor);
        factors.put("reBookAvailable", reBookAvailable);
        factors.put("reBookDiscountZAR", reBookDiscount);
        factors.put("rawFeeZAR", rawFee);
        factors.put("stageCeilingZAR", stageCeiling);
        factors.put("allocationStage", stage != null ? stage.name() : "UNKNOWN");

        LOG.info("Cancellation fee calculated for order {}: finalFeeZAR={}, stage={}, noticeMins={}",
                order.getId(), finalFee, stage, noticePeriodMinutes);

        return new CancellationFeeResult(finalFee, factors, stage);
    }

    /** Convenience overload that uses the current system time. */
    public CancellationFeeResult calculate(Order order) {
        return calculate(order, Instant.now());
    }

    // --- private helpers ---

    private long calculateNoticePeriodMinutes(Date createdDate, Instant now) {
        if (createdDate == null) {
            return 0L;
        }
        long diffMs = now.toEpochMilli() - createdDate.getTime();
        return Math.max(0L, diffMs / 60_000L);
    }

    private double resolveNoticePenalty(CancellationPolicy policy, long noticePeriodMinutes) {
        int[] thresholds = policy.getNoticePeriodThresholdsMinutes();
        double[] penalties = policy.getNoticePeriodPenaltiesZAR();
        if (thresholds == null || penalties == null || thresholds.length == 0) {
            return 0.0;
        }
        // Find the first threshold the notice period falls below
        for (int i = 0; i < thresholds.length; i++) {
            if (noticePeriodMinutes < thresholds[i]) {
                return (i < penalties.length) ? penalties[i] : 0.0;
            }
        }
        return 0.0; // notice period exceeds all thresholds — no penalty
    }

    /**
     * Derives a costs-incurred estimate from the current order stage.
     * A driver has been allocated from STAGE_3 onwards; before that no direct costs are incurred.
     * The estimation is currently a fixed amount that can be made configurable later.
     */
    private double resolveCostsIncurred(OrderStage stage, Order order) {
        if (stage == null) {
            return 0.0;
        }
        // Stages at or beyond STAGE_3 imply driver allocation costs
        return switch (stage) {
            case STAGE_3_READY_FOR_COLLECTION, STAGE_4_ON_THE_ROAD,
                    STAGE_5_ARRIVED, STAGE_6_WITH_CUSTOMER -> {
                // delivery fee already incurred with the driver; zero for now (policy-configurable later)
                yield 0.0;
            }
            default -> 0.0;
        };
    }

    private double resolveStateCeiling(CancellationPolicy policy, OrderStage stage) {
        if (stage == null || policy.getStageCeilings() == null) {
            return 0.0;
        }
        return policy.getStageCeilings().getOrDefault(stage.name(), 0.0);
    }

    static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
