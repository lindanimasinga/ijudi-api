package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.OrderStage;
import io.curiousoft.izinga.commons.order.CancellationPreviewResponse;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * ADR-019: Orchestrates the CPA-compliant customer cancellation preview and confirm flow.
 * <p>
 * PREVIEW (read-only):
 * <ol>
 *   <li>Load persisted order</li>
 *   <li>Calculate fee via {@link CancellationFeeCalculationService}</li>
 *   <li>Sign token: HMAC-SHA256({orderId}|{feeCents}|{expiryEpochSeconds})</li>
 *   <li>Return {@link CancellationPreviewResponse} — NO state change, NO notification</li>
 * </ol>
 * <p>
 * CONFIRM:
 * <ol>
 *   <li>Validate token: signature, expiry, orderId, fee-drift tolerance</li>
 *   <li>Write audit log FIRST — abort with exception if this fails (no silent bypass)</li>
 *   <li>Run payment handler (records amounts for manual reconciliation; no auto-Yoco calls)</li>
 *   <li>Set order stage to CANCELLED, addStatusHistory, set ADR-019 fields, save</li>
 * </ol>
 * <p>
 * There is NO code path where confirm succeeds without a valid {@code feeToken} — including the R0 case.
 */
@Service
public class CustomerCancellationService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerCancellationService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_DELIMITER = "|";
    private static final String TOKEN_DELIMITER_REGEX = "\\|";

    private final OrderRepository orderRepository;
    private final CancellationFeeCalculationService feeCalculationService;
    private final CancellationPolicyCacheService policyCache;
    private final CancellationAuditService auditService;
    private final CancellationPaymentHandler paymentHandler;
    private final String hmacSecret;

    public CustomerCancellationService(
            OrderRepository orderRepository,
            CancellationFeeCalculationService feeCalculationService,
            CancellationPolicyCacheService policyCache,
            CancellationAuditService auditService,
            CancellationPaymentHandler paymentHandler,
            @Value("${cancellation.fee.hmac-secret}") String hmacSecret) {
        this.orderRepository = orderRepository;
        this.feeCalculationService = feeCalculationService;
        this.policyCache = policyCache;
        this.auditService = auditService;
        this.paymentHandler = paymentHandler;
        this.hmacSecret = hmacSecret;
    }

    // -------------------------------------------------------------------------
    // PREVIEW
    // -------------------------------------------------------------------------

    /**
     * Read-only preview.  Does NOT change order stage, deduct payment, or notify anyone.
     * Each call generates a fresh token; prior tokens expire on their own schedule.
     */
    public CancellationPreviewResponse previewCancellationFee(String orderId) {
        Order order = loadOrder(orderId);
        assertCancellable(order);

        Instant now = Instant.now();
        CancellationFeeResult feeResult = feeCalculationService.calculate(order, now);
        double feeZAR = feeResult.getCalculatedFeeZAR();
        double refundZAR = feeResult.netRefundZAR(order.getTotalAmount());

        CancellationPolicy policy = policyCache.getPolicy();
        int validityMinutes = policy.getFeeTokenValidityMinutes();
        Instant expiry = now.plus(validityMinutes, ChronoUnit.MINUTES);

        String token = buildToken(orderId, feeZAR, expiry.getEpochSecond());

        String factorSummary = buildFactorSummary(feeResult);

        LOG.info("Cancellation preview for order {}: feeZAR={}, refundZAR={}, expiresAt={}",
                orderId, feeZAR, refundZAR, expiry);

        return new CancellationPreviewResponse(
                orderId,
                feeZAR,
                refundZAR,
                factorSummary,
                token,
                Date.from(expiry)
        );
    }

    // -------------------------------------------------------------------------
    // CONFIRM
    // -------------------------------------------------------------------------

    /**
     * Confirms a customer-initiated cancellation after token validation.
     *
     * @throws IllegalArgumentException for invalid/expired/mismatched tokens (HTTP 400)
     * @throws IllegalStateException    if the audit log write fails (HTTP 500 — do not proceed)
     */
    public Order confirmCustomerCancelOrder(String orderId, String feeToken) {
        if (feeToken == null || feeToken.isBlank()) {
            throw new IllegalArgumentException(
                    "feeToken is required. Obtain it via GET /order/" + orderId + "/cancellation-preview first.");
        }

        Order order = loadOrder(orderId);
        assertCancellable(order);

        // --- Token validation ---
        String[] parts = feeToken.split(TOKEN_DELIMITER_REGEX, -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid feeToken format.");
        }

        String tokenOrderId   = parts[0];
        String tokenFeeCents  = parts[1];
        String tokenExpiry    = parts[2];
        String tokenHmac      = parts[3];

        // 1. orderId match
        if (!orderId.equals(tokenOrderId)) {
            throw new IllegalArgumentException(
                    "feeToken orderId mismatch — token is not valid for this order.");
        }

        // 2. expiry
        long expiryEpochSeconds;
        try {
            expiryEpochSeconds = Long.parseLong(tokenExpiry);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid feeToken: unparseable expiry.");
        }
        if (Instant.now().getEpochSecond() > expiryEpochSeconds) {
            throw new IllegalArgumentException(
                    "feeToken has expired. Please call GET /order/" + orderId + "/cancellation-preview again.");
        }

        // 3. HMAC signature (constant-time comparison to prevent timing attacks)
        String payload = tokenOrderId + TOKEN_DELIMITER + tokenFeeCents + TOKEN_DELIMITER + tokenExpiry;
        String expectedHmac = computeHmac(payload);
        if (!MessageDigest.isEqual(
                expectedHmac.getBytes(StandardCharsets.UTF_8),
                tokenHmac.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid feeToken: signature verification failed.");
        }

        // 4. Fee drift tolerance — recalculate and compare as cents integers
        long storedFeeCents;
        try {
            storedFeeCents = Long.parseLong(tokenFeeCents);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid feeToken: unparseable fee amount.");
        }

        CancellationPolicy policy = policyCache.getPolicy();
        CancellationFeeResult freshFeeResult = feeCalculationService.calculate(order);
        long freshFeeCents = Math.round(freshFeeResult.getCalculatedFeeZAR() * 100.0);
        long toleranceCents = Math.round(policy.getFeeTokenTolerance() * 100.0);

        if (Math.abs(freshFeeCents - storedFeeCents) > toleranceCents) {
            throw new IllegalArgumentException(
                    "Cancellation fee has changed since the token was issued. " +
                    "Please call GET /order/" + orderId + "/cancellation-preview again.");
        }

        double calculatedFeeZAR = freshFeeResult.getCalculatedFeeZAR();
        double netRefundDueZAR  = freshFeeResult.netRefundZAR(order.getTotalAmount());

        // --- Payment handler: get result BEFORE audit so we can record the handler name.
        // PHASE 2 ORDERING CONSTRAINT (architectural note for future implementors):
        // ManualReconciliationPaymentHandler has zero external side effects — no Yoco call,
        // no money moved — so this ordering (payment handler before audit write) is safe for
        // Phase 1. If a future payment handler (Phase 2 / Option B) makes real Yoco API calls,
        // this ordering MUST be reversed: (1) write audit first, then (2) invoke payment action.
        // Running payment before audit in that scenario would leave a completed charge with no
        // CPA-compliant record if the audit write fails. ---
        CancellationPaymentResult paymentResult = paymentHandler.handle(order, calculatedFeeZAR, netRefundDueZAR);

        // --- Audit log FIRST — abort if it fails (CPA compliance requires this) ---
        CancellationAuditLog auditEntry;
        try {
            auditEntry = auditService.insert(
                    orderId,
                    order.getCustomerId(),
                    calculatedFeeZAR,
                    netRefundDueZAR,
                    freshFeeResult,
                    feeToken,
                    order.getPaymentType() != null ? order.getPaymentType().name() : "UNKNOWN",
                    paymentResult
            );
        } catch (Exception ex) {
            LOG.error("CRITICAL: Audit log write failed for orderId={}. Cancellation aborted. Error: {}",
                    orderId, ex.getMessage(), ex);
            throw new IllegalStateException(
                    "Cancellation audit log write failed. The cancellation has not been processed. " +
                    "Please try again or contact support.", ex);
        }

        // --- Update order stage ---
        order.setStage(OrderStage.CANCELLED);
        order.addStatusHistory(OrderStage.CANCELLED);
        order.setCancellationFee(calculatedFeeZAR);
        order.setCancellationConfirmedAt(new Date());
        order.setCancellationAuditId(auditEntry.getId());

        order = orderRepository.save(order);

        LOG.info("Customer cancellation confirmed: orderId={} feeZAR={} auditId={}",
                orderId, calculatedFeeZAR, auditEntry.getId());

        return order;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Order loadOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order with id " + orderId + " not found."));
    }

    private void assertCancellable(Order order) {
        OrderStage stage = order.getStage();
        if (stage == OrderStage.CANCELLED) {
            throw new IllegalArgumentException(
                    "Order " + order.getId() + " is already cancelled.");
        }
        if (stage == OrderStage.STAGE_7_ALL_PAID) {
            throw new IllegalArgumentException(
                    "Order " + order.getId() + " is already completed and cannot be cancelled.");
        }
    }

    /**
     * Builds and signs a fee token.
     * Format: {@code orderId|feeCents|expiryEpochSeconds|hmacBase64}
     */
    String buildToken(String orderId, double feeZAR, long expiryEpochSeconds) {
        long feeCents = Math.round(feeZAR * 100.0);
        String payload = orderId + TOKEN_DELIMITER + feeCents + TOKEN_DELIMITER + expiryEpochSeconds;
        String hmac = computeHmac(payload);
        return payload + TOKEN_DELIMITER + hmac;
    }

    String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    private String buildFactorSummary(CancellationFeeResult result) {
        StringBuilder sb = new StringBuilder();
        result.getFactorBreakdown().forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
        return sb.toString().trim();
    }
}
