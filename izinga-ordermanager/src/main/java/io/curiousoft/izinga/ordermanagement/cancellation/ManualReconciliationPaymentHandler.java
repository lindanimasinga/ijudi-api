package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ADR-019: Day-one {@link CancellationPaymentHandler} implementation.
 * <p>
 * No automatic Yoco refund or charge is issued.  The calculated fee and net refund-due amounts
 * are recorded in the audit log by {@link CancellationAuditService}; iZinga ops process the
 * net settlement manually. This treatment is uniform across all payment types (YOCO, CASH,
 * SPEED_POINT, etc.).
 */
@Component
public class ManualReconciliationPaymentHandler implements CancellationPaymentHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManualReconciliationPaymentHandler.class);
    static final String HANDLER_NAME = "ManualReconciliationPaymentHandler";

    @Override
    public CancellationPaymentResult handle(Order order, double calculatedFeeZAR, double netRefundDueZAR) {
        LOG.info("Cancellation payment disposition for order {}: paymentType={}, feeZAR={}, netRefundDueZAR={} — PENDING MANUAL RECONCILIATION",
                order.getId(), order.getPaymentType(), calculatedFeeZAR, netRefundDueZAR);
        return new CancellationPaymentResult(
                CancellationPaymentResult.Status.PENDING_MANUAL_RECONCILIATION,
                HANDLER_NAME,
                String.format("paymentType=%s feeZAR=%.2f netRefundDueZAR=%.2f",
                        order.getPaymentType(), calculatedFeeZAR, netRefundDueZAR)
        );
    }
}
