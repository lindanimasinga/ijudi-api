package io.curiousoft.izinga.ordermanagement.auth;

import com.google.firebase.auth.FirebaseAuthException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for WhatsApp OTP login.
 *
 * POST /auth/whatsapp/otp/send   — unauthenticated; rate-limited per-phone and per-IP
 * POST /auth/whatsapp/otp/verify — unauthenticated; returns Firebase custom token
 *
 * Both endpoints are declared with explicit POST-only matchers in SecurityConfig (SEC-01).
 * Neither falls through to anyRequest().permitAll() — they have their own matcher entries.
 */
@RestController
@RequestMapping("/auth/whatsapp/otp")
public class WhatsAppOtpController {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppOtpController.class);

    private final WhatsAppOtpService otpService;

    public WhatsAppOtpController(WhatsAppOtpService otpService) {
        this.otpService = otpService;
    }

    /**
     * Sends a 6-digit OTP via WhatsApp to the given mobile number.
     *
     * Request body: { "mobileNumber": "+27821234567" }
     * Response 204 on success.
     * Response 429 on rate limit exceeded.
     * Response 400 on invalid number format.
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestBody SendOtpRequest request,
                                     HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        try {
            otpService.sendOtp(request.mobileNumber(), clientIp);
            return ResponseEntity.noContent().build();
        } catch (WhatsAppOtpException e) {
            LOG.warn("OTP send rejected for ip={}: {}", clientIp, e.getMessage());
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("wait") || msg != null && msg.toLowerCase().contains("too many")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    /**
     * Verifies the OTP code and returns a Firebase custom token.
     *
     * Request body: { "mobileNumber": "+27821234567", "code": "123456" }
     * Response 200: { "customToken": "..." }
     * Response 401 on invalid/expired/exceeded code.
     * Response 400 on invalid number format.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        try {
            String customToken = otpService.verifyOtp(request.mobileNumber(), request.code());
            return ResponseEntity.ok(Map.of("customToken", customToken));
        } catch (WhatsAppOtpException e) {
            LOG.warn("OTP verify rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        } catch (FirebaseAuthException e) {
            LOG.error("Firebase error during OTP verify", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Authentication service error. Please try again."));
        }
    }

    // --- Request / response records ---

    public record SendOtpRequest(String mobileNumber) {}
    public record VerifyOtpRequest(String mobileNumber, String code) {}

    // --- IP resolution ---

    /**
     * Resolves the originating client IP, respecting X-Forwarded-For for proxied deployments.
     * Returns the first (leftmost) IP in X-Forwarded-For, which is the original client.
     * Falls back to getRemoteAddr().
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
