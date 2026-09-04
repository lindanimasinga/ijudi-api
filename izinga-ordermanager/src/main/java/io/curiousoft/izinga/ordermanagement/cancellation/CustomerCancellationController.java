package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.order.CancellationPreviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ADR-019: New endpoints for the CPA-compliant customer-initiated cancellation flow.
 * <p>
 * These are ADDITIVE — {@code DELETE /order/{id}} (store-owner cancel) is untouched.
 * <p>
 * Flow:
 * <ol>
 *   <li>{@code GET /order/{id}/cancellation-preview} — read-only, returns fee + signed token</li>
 *   <li>{@code POST /order/{id}/customer-cancel} — confirm, requires the signed token</li>
 * </ol>
 */
@RestController
@RequestMapping({"/order", "//order"})
public class CustomerCancellationController {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerCancellationController.class);

    private final CustomerCancellationService cancellationService;

    public CustomerCancellationController(CustomerCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    /**
     * Read-only preview of the cancellation fee.
     * Does NOT change order state, deduct payment, or notify anyone.
     * Returns a short-lived signed {@code feeToken} that must be supplied to confirm.
     */
    @GetMapping(value = "/{id}/cancellation-preview", produces = "application/json")
    public ResponseEntity<?> previewCancellationFee(@PathVariable String id) {
        try {
            CancellationPreviewResponse response = cancellationService.previewCancellationFee(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            LOG.warn("Cancellation preview rejected for order {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            LOG.error("Cancellation preview failed — policy not seeded for order {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Confirms a customer-initiated cancellation.
     * <p>
     * Requires a valid {@code feeToken} obtained from {@code GET /order/{id}/cancellation-preview}.
     * There is NO path through this endpoint that bypasses the token — including when fee is R0.
     *
     * @param id   order id from path
     * @param body JSON body with {@code feeToken}
     */
    @PostMapping(value = "/{id}/customer-cancel",
            consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> confirmCustomerCancel(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String feeToken = body != null ? body.get("feeToken") : null;

        try {
            Order cancelled = cancellationService.confirmCustomerCancelOrder(id, feeToken);
            return ResponseEntity.ok(cancelled);
        } catch (IllegalArgumentException e) {
            LOG.warn("Customer cancel rejected for order {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            LOG.error("Customer cancel failed (audit/state error) for order {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
