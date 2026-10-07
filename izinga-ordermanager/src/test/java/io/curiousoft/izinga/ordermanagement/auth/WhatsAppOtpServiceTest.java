package io.curiousoft.izinga.ordermanagement.auth;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.messaging.whatsapp.lines.WhatsappSenderResolver;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateRequest;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateResponse;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import retrofit2.Call;
import retrofit2.Response;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WhatsAppOtpService.
 *
 * Uses LENIENT strictness because setUp() stubs senderResolver for convenience (used only by
 * sendOtp tests) and because some verify-path tests create local mocks that are only partially
 * consumed (e.g. the notFound FirebaseAuthException in the new-user path is thrown twice but
 * getAuthErrorCode() is only queried on the first catch).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WhatsAppOtpServiceTest {

    @Mock private WhatsAppOtpRepository otpRepository;
    @Mock private WhatsAppService whatsAppService;
    @Mock private WhatsappConfig whatsappConfig;
    @Mock private FirebaseAuth firebaseAuth;
    @Mock private UserProfileService userProfileService;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private WhatsappSenderResolver senderResolver;

    private WhatsAppOtpService service;

    @BeforeEach
    public void setUp() {
        when(senderResolver.resolve(any(Audience.class), any())).thenReturn("testPhoneId");
        service = new WhatsAppOtpService(
                otpRepository, whatsAppService, whatsappConfig,
                firebaseAuth, userProfileService, userProfileRepo, senderResolver);
    }

    // ===================== normalizeMobileNumber =====================

    @Test
    public void normalizeMobileNumber_withLocalFormat_returnsInternational() throws WhatsAppOtpException {
        assertEquals("+27821234567", service.normalizeMobileNumber("0821234567"));
    }

    @Test
    public void normalizeMobileNumber_withInternationalPlusFormat_returnsNormalized() throws WhatsAppOtpException {
        assertEquals("+27821234567", service.normalizeMobileNumber("+27821234567"));
    }

    @Test
    public void normalizeMobileNumber_withoutPlusPrefix_returnsNormalized() throws WhatsAppOtpException {
        assertEquals("+27821234567", service.normalizeMobileNumber("27821234567"));
    }

    @Test
    public void normalizeMobileNumber_tooShort_throwsException() {
        assertThrows(WhatsAppOtpException.class, () -> service.normalizeMobileNumber("12345"));
    }

    @Test
    public void normalizeMobileNumber_null_throwsException() {
        assertThrows(WhatsAppOtpException.class, () -> service.normalizeMobileNumber(null));
    }

    // ===================== hashCode =====================

    @Test
    public void hashCode_deterministicForSameInputs() {
        String h1 = service.hashCode("+27821234567", "123456");
        String h2 = service.hashCode("+27821234567", "123456");
        assertEquals(h1, h2);
    }

    @Test
    public void hashCode_differentForDifferentCodes() {
        String h1 = service.hashCode("+27821234567", "123456");
        String h2 = service.hashCode("+27821234567", "654321");
        assertNotEquals(h1, h2);
    }

    @Test
    public void hashCode_differentForDifferentPhones() {
        String h1 = service.hashCode("+27821234567", "123456");
        String h2 = service.hashCode("+27829999999", "123456");
        assertNotEquals(h1, h2);
    }

    @Test
    public void hashCode_returnsHexString() {
        String hash = service.hashCode("+27821234567", "123456");
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    // ===================== generateCode =====================

    @Test
    public void generateCode_is6Digits() {
        for (int i = 0; i < 20; i++) {
            String code = service.generateCode();
            assertEquals(6, code.length());
            assertTrue(Integer.parseInt(code) >= 100000);
            assertTrue(Integer.parseInt(code) <= 999999);
        }
    }

    // ===================== sendOtp =====================

    @Test
    public void sendOtp_happyPath_savesDocAndSendsMessage() throws Exception {
        mockSuccessfulWhatsAppSend();
        when(whatsappConfig.phoneId()).thenReturn("testPhoneId");

        service.sendOtp("0821234567", "192.168.1.1");

        verify(otpRepository).save(argThat(doc ->
                "+27821234567".equals(doc.getMobileNumber())
                        && doc.getCodeHash() != null
                        && !doc.isUsed()
                        && doc.getAttemptCount() == 0));
        verify(whatsAppService).sendMessage(eq("testPhoneId"), any(WhatsappTemplateRequest.class));
    }

    @Test
    public void sendOtp_templateRecipientInBody_notInUrlPath() throws Exception {
        // SSRF check: verify the normalized recipient number goes into WhatsappTemplateRequest.to
        // (the request body), and the phone-line ID (from senderResolver) goes into the URL path.
        // The critical invariant is that the URL path arg is the server-configured phone line ID,
        // never the user-supplied recipient number.
        mockSuccessfulWhatsAppSend();
        // senderResolver is already stubbed in setUp() to return "testPhoneId" — this is the
        // server-side phone line ID that should appear in the URL path, not the recipient number.

        service.sendOtp("0821234567", "10.0.0.1");

        ArgumentCaptor<WhatsappTemplateRequest> captor =
                ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        ArgumentCaptor<String> phoneIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppService).sendMessage(phoneIdCaptor.capture(), captor.capture());

        // URL-path phoneId must be the server-configured line ID (from senderResolver), not the recipient
        assertEquals("testPhoneId", phoneIdCaptor.getValue());
        // Confirm the recipient number is NOT used as the phoneId in the URL path (SSRF guard)
        assertNotEquals("+27821234567", phoneIdCaptor.getValue());
        // recipient goes into .to field (request body)
        assertEquals("+27821234567", captor.getValue().getTo());
    }

    @Test
    public void sendOtp_withinCooldown_throwsRateLimitException() throws Exception {
        mockSuccessfulWhatsAppSend();
        when(whatsappConfig.phoneId()).thenReturn("testPhoneId");

        service.sendOtp("0821234567", "192.168.1.1");
        // Second call within 60 s should throw
        assertThrows(WhatsAppOtpException.class, () -> service.sendOtp("0821234567", "192.168.1.1"));
    }

    @Test
    public void sendOtp_ipHourlyLimitExceeded_throwsRateLimitException() throws Exception {
        // Use a fresh service instance to avoid cross-test rate limit state
        WhatsAppOtpService freshService = new WhatsAppOtpService(
                otpRepository, whatsAppService, whatsappConfig,
                firebaseAuth, userProfileService, userProfileRepo, senderResolver);

        // Inject the IP times directly to avoid the 60s cooldown
        var ipTimesField = WhatsAppOtpService.class.getDeclaredField("ipRequestTimes");
        ipTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>> ipTimes =
                (java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>>) ipTimesField.get(freshService);
        java.util.List<Long> times = new java.util.concurrent.CopyOnWriteArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) times.add(now - 1000L * i);
        ipTimes.put("attacker-ip", times);

        assertThrows(WhatsAppOtpException.class, () -> freshService.sendOtp("0829999999", "attacker-ip"));
    }

    @Test
    public void sendOtp_phoneHourlyLimitExceeded_throwsRateLimitException() throws Exception {
        WhatsAppOtpService freshService = new WhatsAppOtpService(
                otpRepository, whatsAppService, whatsappConfig,
                firebaseAuth, userProfileService, userProfileRepo, senderResolver);

        var sendTimesField = WhatsAppOtpService.class.getDeclaredField("phoneSendTimes");
        sendTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>> sendTimes =
                (java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>>) sendTimesField.get(freshService);
        java.util.List<Long> times = new java.util.concurrent.CopyOnWriteArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) times.add(now - 1000L * i);
        sendTimes.put("+27829999999", times);

        assertThrows(WhatsAppOtpException.class, () -> freshService.sendOtp("0829999999", "some-ip"));
    }

    // ===================== verifyOtp =====================

    @Test
    public void verifyOtp_happyPath_returnsCustomToken() throws Exception {
        String normalized = "+27821234567";
        String code = "123456";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("doc1", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("doc1")).thenReturn(doc);

        var firebaseUser = mock(UserRecord.class);
        when(firebaseUser.getUid()).thenReturn("uid-abc");
        when(userProfileService.findUserByPhone(normalized)).thenReturn(mock(UserProfile.class));
        when(firebaseAuth.getUserByPhoneNumber(normalized)).thenReturn(firebaseUser);
        when(firebaseAuth.createCustomToken("uid-abc", java.util.Map.of("phone_number", normalized)))
                .thenReturn("firebase-custom-token-xyz");

        String token = service.verifyOtp("0821234567", code);
        assertEquals("firebase-custom-token-xyz", token);

        // Confirm cleanup ran
        verify(otpRepository).deleteByMobileNumber(normalized);
    }

    @Test
    public void verifyOtp_customTokenIncludesPhoneNumberClaim() throws Exception {
        // SEC critical: must call createCustomToken(uid, Map.of("phone_number", normalized))
        String normalized = "+27821234567";
        String code = "654321";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("doc2", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("doc2")).thenReturn(doc);

        var firebaseUser = mock(UserRecord.class);
        when(firebaseUser.getUid()).thenReturn("uid-xyz");
        when(userProfileService.findUserByPhone(normalized)).thenReturn(mock(UserProfile.class));
        when(firebaseAuth.getUserByPhoneNumber(normalized)).thenReturn(firebaseUser);
        when(firebaseAuth.createCustomToken(anyString(), anyMap())).thenReturn("token");

        service.verifyOtp("0821234567", code);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> claimsCaptor =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(firebaseAuth).createCustomToken(eq("uid-xyz"), claimsCaptor.capture());
        assertEquals(normalized, claimsCaptor.getValue().get("phone_number"));
    }

    @Test
    public void verifyOtp_noActiveOtp_throwsException() {
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc("+27821234567"))
                .thenReturn(Optional.empty());
        assertThrows(WhatsAppOtpException.class, () -> service.verifyOtp("0821234567", "123456"));
    }

    @Test
    public void verifyOtp_wrongCode_incrementsAttemptAndThrows() throws Exception {
        String normalized = "+27821234567";
        var doc = makeDoc("doc3", normalized, "wronghash", 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));

        var updated = makeDoc("doc3", normalized, "wronghash", 1, false);
        when(otpRepository.atomicIncrementAttempt("doc3")).thenReturn(updated);

        assertThrows(WhatsAppOtpException.class, () -> service.verifyOtp("0821234567", "999999"));

        verify(otpRepository).atomicIncrementAttempt("doc3");
    }

    @Test
    public void verifyOtp_maxAttemptsReached_throwsException() throws Exception {
        String normalized = "+27821234567";
        var doc = makeDoc("doc4", normalized, "somehash", WhatsAppOtpService.MAX_VERIFY_ATTEMPTS, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));

        assertThrows(WhatsAppOtpException.class, () -> service.verifyOtp("0821234567", "123456"));
        // Should throw before touching Firebase; no interaction with atomicMarkUsed
        verify(otpRepository, never()).atomicMarkUsed(anyString());
    }

    @Test
    public void verifyOtp_atomicMarkUsedReturnsNull_throwsException() throws Exception {
        // SEC-03: concurrent replay attempt — atomicMarkUsed returns null meaning already claimed
        String normalized = "+27821234567";
        String code = "111111";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("doc5", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("doc5")).thenReturn(null); // concurrent claim won

        assertThrows(WhatsAppOtpException.class, () -> service.verifyOtp("0821234567", code));
    }

    @Test
    public void verifyOtp_noExistingProfile_createsFirebaseUserAndProfile() throws Exception {
        String normalized = "+27829999999";
        String code = "222222";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("doc6", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("doc6")).thenReturn(doc);

        // No existing profile
        when(userProfileService.findUserByPhone(normalized)).thenReturn(null);
        // No existing Firebase user — stub getAuthErrorCode() so isUserNotFound() detects it via
        // the primary auth-specific code check (not the message-contains fallback).
        FirebaseAuthException notFound = mock(FirebaseAuthException.class);
        when(notFound.getAuthErrorCode()).thenReturn(AuthErrorCode.USER_NOT_FOUND);
        when(firebaseAuth.getUserByPhoneNumber(normalized)).thenThrow(notFound);

        var newUser = mock(UserRecord.class);
        when(newUser.getUid()).thenReturn("new-uid-123");
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(newUser);
        // createCustomToken is called with a UUID generated in resolveOrCreateFirebaseUser;
        // use anyString() since the UID is not deterministic from the test's perspective.
        when(firebaseAuth.createCustomToken(anyString(), anyMap())).thenReturn("token-new");

        String token = service.verifyOtp("0829999999", code);
        assertEquals("token-new", token);
        verify(userProfileRepo).save(any(UserProfile.class));
    }

    /**
     * Regression test for the production bug where brand-new signups crashed with HTTP 500.
     *
     * Root cause: isUserNotFound() checked e.getErrorCode().name() against "USER_NOT_FOUND",
     * but getErrorCode() returns the base ErrorCode enum which has no USER_NOT_FOUND constant
     * (only NOT_FOUND). The auth-specific detail lives on getAuthErrorCode() which returns
     * AuthErrorCode — that enum does have USER_NOT_FOUND. Additionally, the message-contains
     * fallback checked for the literal substring "USER_NOT_FOUND" but the real Firebase SDK
     * exception message is human-readable prose ("No user record found for the provided phone
     * number: ...") which never contains that substring.
     *
     * As a result both conditions in the old isUserNotFound() were always false for the
     * not-found case, the exception was re-thrown instead of triggering the create-user
     * recovery path, and every brand-new signup (driver, customer, business) crashed.
     *
     * This test reproduces the exact production scenario. It would have thrown
     * FirebaseAuthException (HTTP 500) on the old code; with the fix it returns a token.
     */
    @Test
    public void verifyOtp_brandNewPhone_realFirebaseExceptionMessage_succeedsAndCreatesIdentity()
            throws Exception {
        String normalized = "+27839001122";
        String code = "777777";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("docBrandNew", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("docBrandNew")).thenReturn(doc);

        // No Mongo profile
        when(userProfileService.findUserByPhone(normalized)).thenReturn(null);

        // Simulate the EXACT exception the Firebase SDK throws for a brand-new number:
        // - getAuthErrorCode() returns AuthErrorCode.USER_NOT_FOUND
        // - getMessage() returns prose text with NO "USER_NOT_FOUND" substring
        // With the old isUserNotFound() BOTH checks were false → exception re-thrown → 500.
        FirebaseAuthException sdkException = mock(FirebaseAuthException.class);
        when(sdkException.getAuthErrorCode()).thenReturn(AuthErrorCode.USER_NOT_FOUND);
        when(sdkException.getMessage())
                .thenReturn("No user record found for the provided phone number: " + normalized);
        when(firebaseAuth.getUserByPhoneNumber(normalized)).thenThrow(sdkException);

        var newUser = mock(UserRecord.class);
        when(newUser.getUid()).thenReturn("brand-new-uid-9001");
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(newUser);
        when(firebaseAuth.createCustomToken(anyString(), anyMap())).thenReturn("token-brand-new");

        // Before fix: throws FirebaseAuthException → HTTP 500.  After fix: returns a token.
        String token = service.verifyOtp("0839001122", code);

        assertNotNull(token);
        assertFalse(token.isBlank());
        // Firebase user creation must have been attempted (brand-new identity minted)
        verify(firebaseAuth).createUser(any(UserRecord.CreateRequest.class));
        // UserProfile must have been persisted with the correct phone number
        verify(userProfileRepo).save(argThat(profile ->
                normalized.equals(profile.getMobileNumber())));
    }

    @Test
    public void verifyOtp_noMongoProfileButFirebaseExists_reusesExistingFirebaseUid() throws Exception {
        // Regression for identity-split bug: when a UserProfile was deleted (e.g. support/QA reset)
        // but the Firebase Auth account still exists for the same phone, the SAME Firebase uid
        // MUST be reused — not a new random one. Minting a new uid would permanently split the
        // person across two Firebase identities, causing every subsequent auth token check to
        // operate against the wrong identity (the real signed-in client carries the original uid).
        String normalized = "+27812815707";
        String code = "345678";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("docReg1", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("docReg1")).thenReturn(doc);

        // No Mongo UserProfile exists (was deleted independently)
        when(userProfileService.findUserByPhone(normalized)).thenReturn(null);
        // Firebase Auth DOES have a verified user for this phone number
        UserRecord existingFirebaseUser = mock(UserRecord.class);
        when(existingFirebaseUser.getUid()).thenReturn("5jIWdGqg5IZ3wAyTtFNDPnRqLrn1");
        when(firebaseAuth.getUserByPhoneNumber(normalized)).thenReturn(existingFirebaseUser);
        when(firebaseAuth.createCustomToken(eq("5jIWdGqg5IZ3wAyTtFNDPnRqLrn1"), anyMap()))
                .thenReturn("token-for-existing-user");

        String token = service.verifyOtp("0812815707", code);

        // Token must be minted for the EXISTING Firebase uid, not a new random one
        assertEquals("token-for-existing-user", token);
        // The missing UserProfile must be re-created against the EXISTING uid
        verify(userProfileRepo).save(argThat(profile ->
                "5jIWdGqg5IZ3wAyTtFNDPnRqLrn1".equals(profile.getId())
                        && normalized.equals(profile.getMobileNumber())));
        // A brand-new Firebase user must NOT be created
        verify(firebaseAuth, never()).createUser(any(UserRecord.CreateRequest.class));
    }

    /**
     * ONB-FIX: Verifies that the placeholder UserProfile created on a brand-new OTP verification
     * has role == null, NOT ProfileRoles.CUSTOMER.
     *
     * A null role is the backend's intentional, first-class signal that this profile is an
     * OTP-verified placeholder — the user has not yet completed their signup form.
     * The frontend should key off role == null to distinguish a placeholder from a user who
     * genuinely completed registration and was assigned an explicit role (e.g. CUSTOMER,
     * MESSENGER, STORE_ADMIN).
     *
     * This invariant is enforced only here (WhatsAppOtpService.createUserProfile) — the
     * self-service POST /user path (UserProfileService.create) still correctly requires a
     * non-null role via validateUserProfileForCreate().
     */
    @Test
    public void verifyOtp_newUser_placeholderProfileHasNullRole() throws Exception {
        String normalized = "+27831000001";
        String code = "888888";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("docNull1", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("docNull1")).thenReturn(doc);

        // Brand-new number: no Mongo profile, no Firebase user
        when(userProfileService.findUserByPhone(normalized)).thenReturn(null);
        FirebaseAuthException notFound = mock(FirebaseAuthException.class);
        when(notFound.getAuthErrorCode()).thenReturn(AuthErrorCode.USER_NOT_FOUND);
        when(firebaseAuth.getUserByPhoneNumber(normalized)).thenThrow(notFound);

        var newUser = mock(UserRecord.class);
        when(newUser.getUid()).thenReturn("uid-null-role-test");
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(newUser);
        when(firebaseAuth.createCustomToken(anyString(), anyMap())).thenReturn("token-null-role");

        service.verifyOtp("0831000001", code);

        // CRITICAL: the saved placeholder must have role == null, never CUSTOMER
        verify(userProfileRepo).save(argThat(profile -> {
            assertNull(
                    profile.getRole(),
                    "OTP placeholder profile must have role=null, got: " + profile.getRole());
            return true;
        }));
    }

    /**
     * ONB-FIX complementary: verifies that when Firebase Auth already has a record for the phone
     * (e.g. re-verify after a profile reset) the re-created placeholder still has role == null.
     */
    @Test
    public void verifyOtp_existingFirebaseUserNoMongoProfile_placeholderHasNullRole() throws Exception {
        String normalized = "+27831000002";
        String code = "999888";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("docNull2", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("docNull2")).thenReturn(doc);

        when(userProfileService.findUserByPhone(normalized)).thenReturn(null);
        UserRecord existingFbUser = mock(UserRecord.class);
        when(existingFbUser.getUid()).thenReturn("existing-uid-null-role");
        when(firebaseAuth.getUserByPhoneNumber(normalized)).thenReturn(existingFbUser);
        when(firebaseAuth.createCustomToken(anyString(), anyMap())).thenReturn("token-existing-null");

        service.verifyOtp("0831000002", code);

        verify(userProfileRepo).save(argThat(profile -> {
            assertNull(
                    profile.getRole(),
                    "Re-created placeholder for existing Firebase user must have role=null, got: " + profile.getRole());
            return true;
        }));
    }

    // ===================== helpers =====================

    private WhatsAppOtpDocument makeDoc(String id, String mobile, String hash, int attempts, boolean used) {
        var doc = new WhatsAppOtpDocument();
        doc.setId(id);
        doc.setMobileNumber(mobile);
        doc.setCodeHash(hash);
        doc.setCreatedAt(Instant.now());
        doc.setAttemptCount(attempts);
        doc.setUsed(used);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private void mockSuccessfulWhatsAppSend() throws Exception {
        Call<WhatsappTemplateResponse> call = mock(Call.class);
        WhatsappTemplateResponse resp = mock(WhatsappTemplateResponse.class);
        when(call.execute()).thenReturn(Response.success(resp));
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(call);
    }
}
