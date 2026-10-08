package io.curiousoft.izinga.ordermanagement.subscription;

import io.curiousoft.izinga.commons.model.SubscriptionTier;
import io.curiousoft.izinga.payfast.service.PayFastCheckoutService;
import io.curiousoft.izinga.payfast.service.PayFastItnHandler;
import io.curiousoft.izinga.payfast.service.PayFastValidateTransientException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 * TIER-BILLING-01 / REQ-02, REQ-04: REST controller for merchant subscription billing.
 *
 * Endpoints:
 * - POST /merchant/subscription/initiate — STORE_ADMIN only; storeId from JWT (IDOR-safe).
 * - POST /merchant/subscription/itn      — PUBLIC; called by PayFast servers; no JWT auth.
 *
 * The public ITN endpoint is explicitly matched as permitAll in SecurityConfig.
 */
@RestController
@RequestMapping("/merchant/subscription")
public class MerchantSubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(MerchantSubscriptionController.class);

    private final PayFastCheckoutService checkoutService;
    private final PayFastItnHandler itnHandler;

    public MerchantSubscriptionController(PayFastCheckoutService checkoutService,
                                          PayFastItnHandler itnHandler) {
        this.checkoutService = checkoutService;
        this.itnHandler = itnHandler;
    }

    /**
     * POST /merchant/subscription/initiate
     *
     * REQ-02 / AC-03: storeId is read exclusively from the JWT storeId claim — never from
     * the request body — to prevent IDOR attacks.
     * REQ-02 / AC-04: FREE tier returns HTTP 400.
     * REQ-02 / AC-05: ICA not accepted returns HTTP 422.
     * REQ-02 / AC-06: Already ACTIVE returns HTTP 409.
     * REQ-02 / AC-07: PENDING_PAYMENT within 30 min returns existing mPaymentId (HTTP 200).
     */
    @PreAuthorize("hasRole('STORE_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/initiate")
    public ResponseEntity<Map<String, String>> initiateSubscription(
            @RequestBody SubscriptionInitiateRequest request,
            Authentication authentication) {

        String storeId = extractJwtStoreId(authentication);
        if (storeId == null || storeId.isBlank()) {
            log.warn("Subscription initiate: no storeId claim in JWT for principal={}",
                    authentication.getName());
            return ResponseEntity.status(422).body(Map.of("error", "STORE_ID_NOT_IN_JWT"));
        }

        String ownerId = authentication.getName();
        SubscriptionTier tier = request.getTier();

        if (tier == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "tier is required"));
        }

        Map<String, String> params = checkoutService.initiateCheckout(storeId, ownerId, tier);
        return ResponseEntity.ok(params);
    }

    /**
     * POST /merchant/subscription/itn
     *
     * REQ-04: PUBLIC endpoint — called by PayFast's servers, not by an authenticated user.
     * Security is enforced by PayFast MD5 signature validation AND server-to-server validate
     * inside [PayFastItnHandler]. See SEC-TB01-01-C/D.
     *
     * HTTP response contract (SEC-TB01-01-C):
     * - [PayFastValidateTransientException] → HTTP 500 (transient validate failure — PayFast retries).
     * - All other exceptions / false returns    → HTTP 200 (permanent rejection — no retry needed).
     * - Successful processing                   → HTTP 200.
     *
     * NOTE: This endpoint is explicitly listed as permitAll in SecurityConfig (SEC-TB01-01-E).
     */
    @PostMapping("/itn")
    public ResponseEntity<Void> handleItn(HttpServletRequest request) {
        Map<String, String> params = parseItnParams(request);
        try {
            itnHandler.handleItn(params);
        } catch (PayFastValidateTransientException e) {
            // SEC-TB01-01-C: transient validate failure → HTTP 500 so PayFast retries delivery.
            // Do NOT return 200 here — that would silently drop a payment that may succeed on retry.
            log.error("PAYFAST_VALIDATE_CALL_FAILED for mPaymentId={} — returning 500 for PayFast retry: {}",
                    params.get("m_payment_id"), e.getMessage());
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            // All other unexpected errors: return 200 to prevent PayFast retrying a permanent failure.
            log.error("Unexpected error in ITN handler for mPaymentId={}: {}",
                    params.get("m_payment_id"), e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Parses the ITN request body as application/x-www-form-urlencoded key=value pairs
     * into a plain Map. PayFast posts ITNs as form-encoded body.
     */
    private Map<String, String> parseItnParams(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();
        try {
            // Try Spring's parameter map first (works if content-type is form-encoded).
            Map<String, String[]> paramMap = request.getParameterMap();
            if (!paramMap.isEmpty()) {
                paramMap.forEach((key, values) -> {
                    if (values != null && values.length > 0) {
                        result.put(key, values[0]);
                    }
                });
                return result;
            }
            // Fallback: read raw body and parse manually.
            BufferedReader reader = request.getReader();
            String body = reader.lines().reduce("", (a, b) -> a + b);
            if (body != null && !body.isBlank()) {
                for (String pair : body.split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length == 2) {
                        result.put(
                            java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8)
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse ITN request body: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Extracts the storeId custom claim from the Firebase JWT.
     * Returns null if not present (handled by the caller).
     */
    private String extractJwtStoreId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = (Jwt) jwtAuth.getCredentials();
            return jwt.getClaimAsString("storeId");
        }
        return null;
    }
}
