package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.OrderStage;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * ADR-019: Cancellation fee policy loaded from the {@code cancellation_policy} MongoDB collection.
 * <p>
 * Business values (thresholds, stage ceilings, industry rate floor) are deliberately left
 * at all-zero defaults here. Actual values will be seeded by Lindani/Hloniphani/Jason once
 * the policy decisions are finalised — no redeploy required, only a document update.
 * <p>
 * The service will throw {@link IllegalStateException} (not NPE) when no document exists.
 */
@Document(collection = "cancellation_policy")
public class CancellationPolicy {

    @Id
    private String id;

    /**
     * Sorted thresholds in minutes. If the notice period (time between order creation and
     * cancellation request) is below noticePeriodThresholdsMinutes[i], the corresponding
     * penalty tier applies. A null or empty list means no notice-period penalty applies.
     */
    private int[] noticePeriodThresholdsMinutes = new int[0];

    /**
     * Penalty amount (ZAR) per notice-period tier indexed identically to
     * {@code noticePeriodThresholdsMinutes}. Must be the same length.
     */
    private double[] noticePeriodPenaltiesZAR = new double[0];

    /**
     * Hard ceiling on the calculated fee per allocation stage.
     * Keys are {@link OrderStage} names; values are maximum ZAR amounts.
     * A missing key means no ceiling for that stage (treat as 0 — no fee allowed).
     */
    private Map<String, Double> stageCeilings = new HashMap<>();

    /**
     * Minimum fee floor from the relevant industry code (ZAR). Zero until set by operations.
     */
    private double industryRateFloor = 0.0;

    /**
     * How long (in minutes) a fee token remains valid. Defaults to 15.
     */
    private int feeTokenValidityMinutes = 15;

    /**
     * Maximum allowable fee drift (ZAR) between token-generation calculation and confirm-time
     * recalculation. Applied as: |freshFeeCents - storedFeeCents| <= round(feeTokenTolerance * 100).
     * Defaults to 0.01 (1 cent).
     */
    private double feeTokenTolerance = 0.01;

    /**
     * Whether rebooking is available by default (reduces fee if true). Config-driven.
     */
    private boolean reBookDefaultAvailable = false;

    // --- accessors ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int[] getNoticePeriodThresholdsMinutes() { return noticePeriodThresholdsMinutes; }
    public void setNoticePeriodThresholdsMinutes(int[] noticePeriodThresholdsMinutes) {
        this.noticePeriodThresholdsMinutes = noticePeriodThresholdsMinutes;
    }

    public double[] getNoticePeriodPenaltiesZAR() { return noticePeriodPenaltiesZAR; }
    public void setNoticePeriodPenaltiesZAR(double[] noticePeriodPenaltiesZAR) {
        this.noticePeriodPenaltiesZAR = noticePeriodPenaltiesZAR;
    }

    public Map<String, Double> getStageCeilings() { return stageCeilings; }
    public void setStageCeilings(Map<String, Double> stageCeilings) {
        this.stageCeilings = stageCeilings;
    }

    public double getIndustryRateFloor() { return industryRateFloor; }
    public void setIndustryRateFloor(double industryRateFloor) {
        this.industryRateFloor = industryRateFloor;
    }

    public int getFeeTokenValidityMinutes() { return feeTokenValidityMinutes; }
    public void setFeeTokenValidityMinutes(int feeTokenValidityMinutes) {
        this.feeTokenValidityMinutes = feeTokenValidityMinutes;
    }

    public double getFeeTokenTolerance() { return feeTokenTolerance; }
    public void setFeeTokenTolerance(double feeTokenTolerance) {
        this.feeTokenTolerance = feeTokenTolerance;
    }

    public boolean isReBookDefaultAvailable() { return reBookDefaultAvailable; }
    public void setReBookDefaultAvailable(boolean reBookDefaultAvailable) {
        this.reBookDefaultAvailable = reBookDefaultAvailable;
    }
}
