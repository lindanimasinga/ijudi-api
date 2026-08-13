package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.Order;

/**
 * ADR-019: Strategy interface for handling the payment side of a customer-initiated cancellation.
 * <p>
 * Current day-one behaviour: all payment types are handled via manual reconciliation
 * ({@link ManualReconciliationPaymentHandler}) — no automatic Yoco calls, no partial refunds.
 * The amounts are recorded in the audit log for iZinga ops to process.
 * <p>
 * The interface is kept so that a different strategy can be wired in via Spring profile or
 * config flag in a future task once Lindani/ops decide what (if any) automation they want.
 */
public interface CancellationPaymentHandler {

    /**
     * Handles the payment disposition for a confirmed customer cancellation.
     *
     * @param order              the persisted order being cancelled
     * @param calculatedFeeZAR   the disclosed cancellation fee (may be R0)
     * @param netRefundDueZAR    order.totalAmount - calculatedFeeZAR (always >= 0)
     * @return a result describing what was done (for logging / audit)
     */
    CancellationPaymentResult handle(Order order, double calculatedFeeZAR, double netRefundDueZAR);
}
