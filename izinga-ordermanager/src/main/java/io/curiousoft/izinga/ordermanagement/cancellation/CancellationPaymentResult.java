package io.curiousoft.izinga.ordermanagement.cancellation;

/**
 * ADR-019: Outcome of {@link CancellationPaymentHandler#handle}.
 */
public class CancellationPaymentResult {

    public enum Status {
        /** No automatic payment action taken; amounts recorded for manual ops reconciliation. */
        PENDING_MANUAL_RECONCILIATION
    }

    private final Status status;
    private final String handlerName;
    private final String notes;

    public CancellationPaymentResult(Status status, String handlerName, String notes) {
        this.status = status;
        this.handlerName = handlerName;
        this.notes = notes;
    }

    public Status getStatus() { return status; }
    public String getHandlerName() { return handlerName; }
    public String getNotes() { return notes; }
}
