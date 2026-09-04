package io.curiousoft.izinga.ordermanagement.cancellation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * ADR-019: Insert-only service for {@link CancellationAuditLog}.
 * <p>
 * No update or delete methods are exposed.  The fee token is stored only as its
 * SHA-256 hash — never raw.
 * <p>
 * A failed insert MUST propagate — callers abort the entire cancellation if this throws.
 */
@Service
public class CancellationAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(CancellationAuditService.class);

    private final CancellationAuditRepository repository;

    public CancellationAuditService(CancellationAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Inserts one immutable audit record.
     *
     * @return the saved log entry (with its generated {@code id})
     * @throws RuntimeException if the insert fails — callers must treat this as fatal
     */
    public CancellationAuditLog insert(
            String orderId,
            String customerId,
            double calculatedFeeZAR,
            double netRefundDueZAR,
            CancellationFeeResult feeResult,
            String rawFeeToken,
            String paymentType,
            CancellationPaymentResult paymentResult) {

        CancellationAuditLog log = new CancellationAuditLog();
        log.setOrderId(orderId);
        log.setCustomerId(customerId);
        log.setCalculatedFeeZAR(calculatedFeeZAR);
        log.setNetRefundDueZAR(netRefundDueZAR);
        log.setAllocationStageAtCalculation(feeResult.getAllocationStageAtCalculation());
        log.setFactorSnapshot(Map.copyOf(feeResult.getFactorBreakdown()));
        log.setConfirmationTimestamp(new Date());
        log.setFeeTokenHash(hashToken(rawFeeToken));
        log.setPaymentType(paymentType);
        log.setPaymentHandlerUsed(paymentResult.getHandlerName());
        log.setPaymentHandlerNotes(paymentResult.getNotes());
        log.setPaymentStatus(paymentResult.getStatus());

        CancellationAuditLog saved = repository.save(log);
        LOG.info("Cancellation audit log inserted: id={} orderId={} feeZAR={} netRefundDueZAR={}",
                saved.getId(), orderId, calculatedFeeZAR, netRefundDueZAR);
        return saved;
    }

    /** SHA-256 of the raw token, Base64-encoded. Never stores the raw token. */
    static String hashToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }
}
