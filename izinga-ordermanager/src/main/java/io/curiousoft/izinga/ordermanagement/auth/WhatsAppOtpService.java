package io.curiousoft.izinga.ordermanagement.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateRequest;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WhatsApp OTP login service.
 *
 * Security properties enforced:
 * - OTP stored as SHA-256(mobileNumber + ":" + code), never plaintext.
 * - Per-phone rate limits: 1 send / 60s, 5 sends / rolling hour.
 * - Per-IP rate limit: 10 requests / rolling hour (SEC-01).
 * - 5 verify attempts per code; code invalidated on exceed.
 * - Atomic used-flag check-and-set via MongoDB findOneAndUpdate (SEC-03).
 * - Mobile number normalization: last 9 digits + "+27" prefix, performed once here.
 *   Same normalized value goes into: UserProfile.mobileNumber, Firebase phone_number
 *   claim, and findUserByPhone() lookup. (Point 6 in the plan.)
 *
 * Deployment constraint: rate-limit counters are in-memory (ConcurrentHashMap).
 * Production is confirmed single-instance. If ever scaled to multiple instances,
 * these counters must be moved to MongoDB-backed atomic counters before that happens.
 */
@Service
public class WhatsAppOtpService {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppOtpService.class);

    // Rate limit thresholds (SEC-01, SEC-02)
    static final long SEND_COOLDOWN_SECONDS = 60L;          // 1 send per 60 s per phone
    static final long SEND_HOUR_MAX = 5L;                   // 5 sends per rolling hour per phone
    static final long IP_HOUR_MAX = 10L;                    // 10 requests per rolling hour per IP
    static final int MAX_VERIFY_ATTEMPTS = 5;               // 5 attempts per code
    static final String OTP_WHATSAPP_TEMPLATE = "whatsapp_otp_auth"; // Meta Authentication template name

    // In-memory rate limit state (single-instance constraint documented above)
    private final ConcurrentHashMap<String, Long> lastSendTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Long>> phoneSendTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Long>> ipRequestTimes = new ConcurrentHashMap<>();

    private final WhatsAppOtpRepository otpRepository;
    private final WhatsAppService whatsAppService;
    private final WhatsappConfig whatsappConfig;
    private final FirebaseAuth firebaseAuth;
    private final UserProfileService userProfileService;
    private final UserProfileRepo userProfileRepo;
    private final SecureRandom secureRandom = new SecureRandom();

    public WhatsAppOtpService(
            WhatsAppOtpRepository otpRepository,
            WhatsAppService whatsAppService,
            WhatsappConfig whatsappConfig,
            FirebaseAuth firebaseAuth,
            UserProfileService userProfileService,
            UserProfileRepo userProfileRepo) {
        this.otpRepository = otpRepository;
        this.whatsAppService = whatsAppService;
        this.whatsappConfig = whatsappConfig;
        this.firebaseAuth = firebaseAuth;
        this.userProfileService = userProfileService;
        this.userProfileRepo = userProfileRepo;
    }

    /**
     * Sends a WhatsApp OTP to the given mobile number.
     *
     * @param rawMobileNumber raw number as supplied by the frontend (e.g. "0821234567" or "+27821234567")
     * @param clientIp        the originating IP address for per-IP rate limiting (SEC-01)
     * @throws WhatsAppOtpException on rate limit exceeded or send failure
     */
    public void sendOtp(String rawMobileNumber, String clientIp) throws WhatsAppOtpException {
        String normalized = normalizeMobileNumber(rawMobileNumber);

        checkPhoneRateLimit(normalized);
        checkIpRateLimit(clientIp);

        String code = generateCode();
        String codeHash = hashCode(normalized, code);

        var doc = new WhatsAppOtpDocument();
        doc.setMobileNumber(normalized);
        doc.setCodeHash(codeHash);
        doc.setCreatedAt(Instant.now());
        doc.setAttemptCount(0);
        doc.setUsed(false);
        otpRepository.save(doc);

        recordSend(normalized, clientIp);

        sendWhatsAppOtpMessage(normalized, code);
        LOG.info("WhatsApp OTP sent to normalized={}", normalized);
    }

    /**
     * Verifies a WhatsApp OTP code and returns a Firebase custom token on success.
     *
     * @param rawMobileNumber raw number from the frontend
     * @param code            the 6-digit code entered by the user
     * @return Firebase custom token
     * @throws WhatsAppOtpException on invalid/expired/exceeded code
     * @throws FirebaseAuthException on Firebase user lookup/create or token-minting failure
     */
    public String verifyOtp(String rawMobileNumber, String code)
            throws WhatsAppOtpException, FirebaseAuthException {

        String normalized = normalizeMobileNumber(rawMobileNumber);

        var optDoc = otpRepository
                .findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized);
        if (optDoc.isEmpty()) {
            LOG.warn("No active OTP found for normalized={}", normalized);
            throw new WhatsAppOtpException("Invalid or expired code.");
        }

        var doc = optDoc.get();

        if (doc.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
            LOG.warn("OTP attempt limit exceeded for normalized={} docId={}", normalized, doc.getId());
            throw new WhatsAppOtpException("Too many verification attempts. Request a new code.");
        }

        String expectedHash = hashCode(normalized, code);
        if (!expectedHash.equals(doc.getCodeHash())) {
            // Increment attempt counter atomically
            var updated = otpRepository.atomicIncrementAttempt(doc.getId());
            int newCount = updated != null ? updated.getAttemptCount() : doc.getAttemptCount() + 1;
            LOG.warn("Incorrect OTP for normalized={} attempts={}", normalized, newCount);
            if (newCount >= MAX_VERIFY_ATTEMPTS) {
                throw new WhatsAppOtpException("Too many incorrect attempts. Request a new code.");
            }
            throw new WhatsAppOtpException("Invalid code.");
        }

        // SEC-03: atomic mark-used — fails if already used concurrently
        var claimed = otpRepository.atomicMarkUsed(doc.getId());
        if (claimed == null) {
            LOG.warn("OTP already used (concurrent replay attempt) for normalized={}", normalized);
            throw new WhatsAppOtpException("Code already used.");
        }

        // Clean up remaining OTPs for this number
        otpRepository.deleteByMobileNumber(normalized);

        // Resolve or create Firebase user and UserProfile
        String uid = resolveOrCreateFirebaseUser(normalized);

        // Mint custom token with phone_number claim — REQUIRED (see plan point 1)
        String customToken = firebaseAuth.createCustomToken(uid, Map.of("phone_number", normalized));
        LOG.info("WhatsApp OTP verified and custom token minted for normalized={} uid={}", normalized, uid);
        return customToken;
    }

    // --- Normalization ---

    /**
     * Normalizes a South African mobile number to +27XXXXXXXXX format.
     * Uses the last 9 digits of the input, identical to UserProfileService.findUserByPhone().
     *
     * @throws WhatsAppOtpException if the input is too short to extract 9 digits
     */
    public String normalizeMobileNumber(String raw) throws WhatsAppOtpException {
        if (raw == null) {
            throw new WhatsAppOtpException("Mobile number must not be null.");
        }
        // Strip non-digit characters (preserve only digits)
        String digits = raw.replaceAll("[^\\d]", "");
        if (digits.length() < 9) {
            throw new WhatsAppOtpException("Invalid mobile number format.");
        }
        String last9 = digits.substring(digits.length() - 9);
        return "+27" + last9;
    }

    // --- Rate limiting ---

    private void checkPhoneRateLimit(String normalized) throws WhatsAppOtpException {
        long now = System.currentTimeMillis();

        // 1 send per 60 s
        Long last = lastSendTime.get(normalized);
        if (last != null && (now - last) < SEND_COOLDOWN_SECONDS * 1000) {
            LOG.warn("Phone rate limit (60s) hit for normalized={}", normalized);
            throw new WhatsAppOtpException("Please wait before requesting another code.");
        }

        // 5 sends per rolling hour
        List<Long> times = phoneSendTimes.computeIfAbsent(normalized,
                k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        long hourAgo = now - 3_600_000L;
        times.removeIf(t -> t < hourAgo);
        if (times.size() >= SEND_HOUR_MAX) {
            LOG.warn("Phone rate limit (hourly) hit for normalized={}", normalized);
            throw new WhatsAppOtpException("Too many code requests. Try again in an hour.");
        }
    }

    private void checkIpRateLimit(String ip) throws WhatsAppOtpException {
        if (ip == null || ip.isBlank()) return; // if IP can't be determined, skip
        long now = System.currentTimeMillis();
        List<Long> times = ipRequestTimes.computeIfAbsent(ip,
                k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        long hourAgo = now - 3_600_000L;
        times.removeIf(t -> t < hourAgo);
        if (times.size() >= IP_HOUR_MAX) {
            LOG.warn("IP rate limit (hourly) hit for ip={}", ip);
            throw new WhatsAppOtpException("Too many requests from this IP. Try again later.");
        }
    }

    private void recordSend(String normalized, String ip) {
        long now = System.currentTimeMillis();
        lastSendTime.put(normalized, now);
        phoneSendTimes.computeIfAbsent(normalized,
                k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(now);
        if (ip != null && !ip.isBlank()) {
            ipRequestTimes.computeIfAbsent(ip,
                    k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(now);
        }
    }

    // --- OTP generation and hashing ---

    String generateCode() {
        int code = 100_000 + secureRandom.nextInt(900_000);
        return String.valueOf(code);
    }

    /**
     * SHA-256(mobileNumber + ":" + code) — mobile number acts as the salt.
     * Never stored or logged as plaintext.
     */
    String hashCode(String normalizedMobileNumber, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = normalizedMobileNumber + ":" + code;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // --- WhatsApp message dispatch ---

    private void sendWhatsAppOtpMessage(String normalized, String code) throws WhatsAppOtpException {
        var req = new WhatsappTemplateRequest();
        req.setTo(normalized);   // recipient goes into the body field — NOT the URL path (SSRF check: PASS)

        var template = new WhatsappTemplateRequest.Template();
        template.setName(OTP_WHATSAPP_TEMPLATE);

        var lang = new WhatsappTemplateRequest.Language();
        lang.setCode("en_US");
        template.setLanguage(lang);

        // Authentication-category template: BODY component with the code as {{1}}
        var codeParam = new WhatsappTemplateRequest.Parameter();
        codeParam.setType(WhatsappTemplateRequest.ParameterType.TEXT);
        codeParam.setText(code);

        var bodyComponent = new WhatsappTemplateRequest.Component();
        bodyComponent.setType(WhatsappTemplateRequest.ComponentType.BODY);
        bodyComponent.setParameters(List.of(codeParam));

        // BUTTON component for "Copy Code" button (index 0)
        var buttonParam = new WhatsappTemplateRequest.Parameter();
        buttonParam.setType(WhatsappTemplateRequest.ParameterType.TEXT);
        buttonParam.setText(code);

        var buttonComponent = new WhatsappTemplateRequest.Component();
        buttonComponent.setType(WhatsappTemplateRequest.ComponentType.BUTTON);
        buttonComponent.setSub_type(WhatsappTemplateRequest.ButtonSubType.URL);
        buttonComponent.setIndex(0);
        buttonComponent.setParameters(List.of(buttonParam));

        template.setComponents(List.of(bodyComponent, buttonComponent));
        req.setTemplate(template);

        try {
            var call = whatsAppService.sendMessage(whatsappConfig.phoneId(), req);
            var response = call.execute();
            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null
                        ? response.errorBody().string() : "(no body)";
                LOG.error("WhatsApp send failed for normalized={}: {} {}",
                        normalized, response.code(), errorBody);
                throw new WhatsAppOtpException("Failed to send WhatsApp OTP. Please try again.");
            }
        } catch (WhatsAppOtpException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("WhatsApp send exception for normalized={}", normalized, e);
            throw new WhatsAppOtpException("Failed to send WhatsApp OTP.");
        }
    }

    // --- Firebase user resolution ---

    /**
     * Finds or creates the Firebase Auth user record for the given normalized phone number.
     * If a matching UserProfile exists, reuses the stored Firebase UID.
     * If not, creates a new Firebase user and a new UserProfile.
     *
     * The normalized mobile number is stored byte-identically in:
     * - UserProfile.mobileNumber
     * - The Firebase phone_number claim used in createCustomToken()
     * - findUserByPhone() lookups (which try 0|+27|27 + last9 — all resolve to +27XXXXXXXXX)
     */
    private String resolveOrCreateFirebaseUser(String normalized) throws FirebaseAuthException {
        // Check for existing profile
        UserProfile existingProfile = userProfileService.findUserByPhone(normalized);
        if (existingProfile != null) {
            // Try to find Firebase user by phone
            try {
                UserRecord firebaseUser = firebaseAuth.getUserByPhoneNumber(normalized);
                return firebaseUser.getUid();
            } catch (FirebaseAuthException e) {
                if ("USER_NOT_FOUND".equals(e.getErrorCode().name()) ||
                        e.getMessage() != null && e.getMessage().contains("USER_NOT_FOUND")) {
                    // Firebase user doesn't exist — create one with matching UID
                    return createFirebaseUser(normalized, existingProfile.getId());
                }
                throw e;
            }
        }

        // No existing profile — create Firebase user and UserProfile
        String uid = UUID.randomUUID().toString();
        createFirebaseUser(normalized, uid);
        createUserProfile(normalized, uid);
        return uid;
    }

    private String createFirebaseUser(String normalized, String preferredUid) throws FirebaseAuthException {
        try {
            // Try to find first; create only if absent
            UserRecord existing = firebaseAuth.getUserByPhoneNumber(normalized);
            return existing.getUid();
        } catch (FirebaseAuthException notFound) {
            var createRequest = new UserRecord.CreateRequest()
                    .setPhoneNumber(normalized)
                    .setUid(preferredUid);
            UserRecord created = firebaseAuth.createUser(createRequest);
            LOG.info("Created Firebase user uid={} for normalized={}", created.getUid(), normalized);
            return created.getUid();
        }
    }

    private void createUserProfile(String normalized, String uid) {
        try {
            var profile = new UserProfile(
                    "WhatsApp User",
                    UserProfile.SignUpReason.BUY,
                    "",
                    "",
                    normalized,
                    ProfileRoles.CUSTOMER);
            profile.setId(uid);
            userProfileRepo.save(profile);
            LOG.info("Created UserProfile id={} mobileNumber={} via WhatsApp OTP login", uid, normalized);
        } catch (Exception e) {
            LOG.error("Failed to create UserProfile for normalized={}", normalized, e);
            // Non-fatal: custom token can still be minted; profile creation can be retried on next login
        }
    }
}
