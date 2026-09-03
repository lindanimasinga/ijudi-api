package io.curiousoft.izinga.ordermanagement.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateRequest;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateResponse;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WhatsAppOtpServiceTest {

    @Mock private WhatsAppOtpRepository otpRepository;
    @Mock private WhatsAppService whatsAppService;
    @Mock private WhatsappConfig whatsappConfig;
    @Mock private FirebaseAuth firebaseAuth;
    @Mock private UserProfileService userProfileService;
    @Mock private UserProfileRepo userProfileRepo;

    private WhatsAppOtpService service;

    @Before
    public void setUp() {
        service = new WhatsAppOtpService(
                otpRepository, whatsAppService, whatsappConfig,
                firebaseAuth, userProfileService, userProfileRepo);
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

    @Test(expected = WhatsAppOtpException.class)
    public void normalizeMobileNumber_tooShort_throwsException() throws WhatsAppOtpException {
        service.normalizeMobileNumber("12345");
    }

    @Test(expected = WhatsAppOtpException.class)
    public void normalizeMobileNumber_null_throwsException() throws WhatsAppOtpException {
        service.normalizeMobileNumber(null);
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
        // SSRF check: verify the normalized number goes into WhatsappTemplateRequest.to (body),
        // not into the phoneId path parameter
        mockSuccessfulWhatsAppSend();
        when(whatsappConfig.phoneId()).thenReturn("businessPhoneId");

        service.sendOtp("0821234567", "10.0.0.1");

        ArgumentCaptor<WhatsappTemplateRequest> captor =
                ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        ArgumentCaptor<String> phoneIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppService).sendMessage(phoneIdCaptor.capture(), captor.capture());

        // phoneId in URL must be the WhatsApp Business phone ID, not the recipient
        assertEquals("businessPhoneId", phoneIdCaptor.getValue());
        // recipient goes into .to field (request body)
        assertEquals("+27821234567", captor.getValue().getTo());
    }

    @Test(expected = WhatsAppOtpException.class)
    public void sendOtp_withinCooldown_throwsRateLimitException() throws Exception {
        mockSuccessfulWhatsAppSend();
        when(whatsappConfig.phoneId()).thenReturn("testPhoneId");

        service.sendOtp("0821234567", "192.168.1.1");
        // Second call within 60 s should throw
        service.sendOtp("0821234567", "192.168.1.1");
    }

    @Test(expected = WhatsAppOtpException.class)
    public void sendOtp_ipHourlyLimitExceeded_throwsRateLimitException() throws Exception {
        mockSuccessfulWhatsAppSend();
        when(whatsappConfig.phoneId()).thenReturn("testPhoneId");

        // Use a fresh service instance to avoid cross-test rate limit state
        WhatsAppOtpService freshService = new WhatsAppOtpService(
                otpRepository, whatsAppService, whatsappConfig,
                firebaseAuth, userProfileService, userProfileRepo);

        // Exhaust IP limit with different phone numbers (simulating attacker cycling numbers)
        // We need to send 10 requests from the same IP to different phone numbers
        // but the per-phone cooldown prevents same-number rapid fire.
        // Use reflection to inject the IP times directly to avoid the 60s cooldown.
        var ipTimesField = WhatsAppOtpService.class.getDeclaredField("ipRequestTimes");
        ipTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>> ipTimes =
                (java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>>) ipTimesField.get(freshService);
        java.util.List<Long> times = new java.util.concurrent.CopyOnWriteArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) times.add(now - 1000L * i);
        ipTimes.put("attacker-ip", times);

        freshService.sendOtp("0829999999", "attacker-ip");
    }

    @Test(expected = WhatsAppOtpException.class)
    public void sendOtp_phoneHourlyLimitExceeded_throwsRateLimitException() throws Exception {
        WhatsAppOtpService freshService = new WhatsAppOtpService(
                otpRepository, whatsAppService, whatsappConfig,
                firebaseAuth, userProfileService, userProfileRepo);

        var sendTimesField = WhatsAppOtpService.class.getDeclaredField("phoneSendTimes");
        sendTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>> sendTimes =
                (java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>>) sendTimesField.get(freshService);
        java.util.List<Long> times = new java.util.concurrent.CopyOnWriteArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) times.add(now - 1000L * i);
        sendTimes.put("+27829999999", times);

        freshService.sendOtp("0829999999", "some-ip");
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

    @Test(expected = WhatsAppOtpException.class)
    public void verifyOtp_noActiveOtp_throwsException() throws Exception {
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc("+27821234567"))
                .thenReturn(Optional.empty());
        service.verifyOtp("0821234567", "123456");
    }

    @Test(expected = WhatsAppOtpException.class)
    public void verifyOtp_wrongCode_incrementsAttemptAndThrows() throws Exception {
        String normalized = "+27821234567";
        var doc = makeDoc("doc3", normalized, "wronghash", 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));

        var updated = makeDoc("doc3", normalized, "wronghash", 1, false);
        when(otpRepository.atomicIncrementAttempt("doc3")).thenReturn(updated);

        service.verifyOtp("0821234567", "999999");

        verify(otpRepository).atomicIncrementAttempt("doc3");
    }

    @Test(expected = WhatsAppOtpException.class)
    public void verifyOtp_maxAttemptsReached_throwsException() throws Exception {
        String normalized = "+27821234567";
        var doc = makeDoc("doc4", normalized, "somehash", WhatsAppOtpService.MAX_VERIFY_ATTEMPTS, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));

        service.verifyOtp("0821234567", "123456");
        // Should throw before touching Firebase; no interaction with atomicMarkUsed
        verify(otpRepository, never()).atomicMarkUsed(anyString());
    }

    @Test(expected = WhatsAppOtpException.class)
    public void verifyOtp_atomicMarkUsedReturnsNull_throwsException() throws Exception {
        // SEC-03: concurrent replay attempt — atomicMarkUsed returns null meaning already claimed
        String normalized = "+27821234567";
        String code = "111111";
        String hash = service.hashCode(normalized, code);

        var doc = makeDoc("doc5", normalized, hash, 0, false);
        when(otpRepository.findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(normalized))
                .thenReturn(Optional.of(doc));
        when(otpRepository.atomicMarkUsed("doc5")).thenReturn(null); // concurrent claim won

        service.verifyOtp("0821234567", code);
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
        // No existing Firebase user
        FirebaseAuthException notFound = mock(FirebaseAuthException.class);
        when(notFound.getMessage()).thenReturn("USER_NOT_FOUND");
        when(firebaseAuth.getUserByPhoneNumber(normalized)).thenThrow(notFound);
        var newUser = mock(UserRecord.class);
        when(newUser.getUid()).thenReturn("new-uid-123");
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(newUser);
        when(firebaseAuth.createCustomToken(eq("new-uid-123"), anyMap())).thenReturn("token-new");

        String token = service.verifyOtp("0829999999", code);
        assertEquals("token-new", token);
        verify(userProfileRepo).save(any(UserProfile.class));
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
