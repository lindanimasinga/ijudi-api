package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.OrderStage;

import java.util.Collections;
import java.util.Map;

/**
 * ADR-019: Result of {@link CancellationFeeCalculationService#calculate}.
 * R0 is a fully valid, non-exceptional result — the caller must never special-case it away.
 */
public class CancellationFeeResult {

    /** Calculated cancellation fee in ZAR, never negative. */
    private final double calculatedFeeZAR;

    /** Snapshot of each factor contributing to the fee, for disclosure and audit. */
    private final Map<String, Object> factorBreakdown;

    /** Order stage at the moment of calculation — used for the stage-ceiling check. */
    private final OrderStage allocationStageAtCalculation;

    public CancellationFeeResult(double calculatedFeeZAR,
                                 Map<String, Object> factorBreakdown,
                                 OrderStage allocationStageAtCalculation) {
        this.calculatedFeeZAR = calculatedFeeZAR;
        this.factorBreakdown = Collections.unmodifiableMap(factorBreakdown);
        this.allocationStageAtCalculation = allocationStageAtCalculation;
    }

    public double getCalculatedFeeZAR() { return calculatedFeeZAR; }

    public Map<String, Object> getFactorBreakdown() { return factorBreakdown; }

    public OrderStage getAllocationStageAtCalculation() { return allocationStageAtCalculation; }

    /** Convenience: net refund due to customer = totalOrderAmount - calculatedFeeZAR. */
    public double netRefundZAR(double totalOrderAmount) {
        return Math.max(0.0, totalOrderAmount - calculatedFeeZAR);
    }
}
