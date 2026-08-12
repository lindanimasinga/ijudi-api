package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.OrderStage;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

/**
 * ADR-019: Immutable, insert-only audit record written before any payment action
 * for every confirmed customer-initiated cancellation.
 * <p>
 * Once inserted this document must never be updated or deleted — CPA T&amp;Cs clause 8.1(c)
 * compliance requires a complete, tamper-evident trail.
 */
@Document(collection = "cancellation_fee_audit_log")
public class CancellationAuditLog {

    @Id
    private String id;

    private String orderId;
    private String customerId;
    private double calculatedFeeZAR;
    private double netRefundDueZAR;
    private OrderStage allocationStageAtCalculation;
    private Map<String, Object> factorSnapshot;
    private Date confirmationTimestamp;
    /** SHA-256 hash of the fee token — never stored raw. */
    private String feeTokenHash;
    private String paymentType;
    private String paymentHandlerUsed;
    private String paymentHandlerNotes;
    private CancellationPaymentResult.Status paymentStatus;

    public CancellationAuditLog() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public double getCalculatedFeeZAR() { return calculatedFeeZAR; }
    public void setCalculatedFeeZAR(double calculatedFeeZAR) { this.calculatedFeeZAR = calculatedFeeZAR; }

    public double getNetRefundDueZAR() { return netRefundDueZAR; }
    public void setNetRefundDueZAR(double netRefundDueZAR) { this.netRefundDueZAR = netRefundDueZAR; }

    public OrderStage getAllocationStageAtCalculation() { return allocationStageAtCalculation; }
    public void setAllocationStageAtCalculation(OrderStage allocationStageAtCalculation) {
        this.allocationStageAtCalculation = allocationStageAtCalculation;
    }

    public Map<String, Object> getFactorSnapshot() { return factorSnapshot; }
    public void setFactorSnapshot(Map<String, Object> factorSnapshot) { this.factorSnapshot = factorSnapshot; }

    public Date getConfirmationTimestamp() { return confirmationTimestamp; }
    public void setConfirmationTimestamp(Date confirmationTimestamp) {
        this.confirmationTimestamp = confirmationTimestamp;
    }

    public String getFeeTokenHash() { return feeTokenHash; }
    public void setFeeTokenHash(String feeTokenHash) { this.feeTokenHash = feeTokenHash; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public String getPaymentHandlerUsed() { return paymentHandlerUsed; }
    public void setPaymentHandlerUsed(String paymentHandlerUsed) { this.paymentHandlerUsed = paymentHandlerUsed; }

    public String getPaymentHandlerNotes() { return paymentHandlerNotes; }
    public void setPaymentHandlerNotes(String paymentHandlerNotes) { this.paymentHandlerNotes = paymentHandlerNotes; }

    public CancellationPaymentResult.Status getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(CancellationPaymentResult.Status paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
