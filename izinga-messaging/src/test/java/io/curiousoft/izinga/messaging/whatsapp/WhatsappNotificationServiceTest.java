package io.curiousoft.izinga.messaging.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.messaging.whatsapp.lines.WhatsappSenderResolver;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateRequest;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTemplateResponse;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTextRequest;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTextResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import retrofit2.Call;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappNotificationServiceTest {

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private WhatsappConfig whatsappConfig;

    @Mock
    private WhatsappSenderResolver senderResolver;

    /** ADR-028: cooldown repository mock for throttle tests. */
    @Mock
    private PayoutNotificationCooldownRepository cooldownRepo;

    @SuppressWarnings("unchecked")
    private final Call<WhatsappTemplateResponse> callMock = mock(Call.class);

    /** Service under test — cooldown repository is null (no-throttle mode). */
    private WhatsappNotificationService service;

    /** Service under test — cooldown repository is wired (throttle mode for ADR-028 tests). */
    private WhatsappNotificationService serviceWithCooldown;

    @BeforeEach
    void setUp() {
        // REQ-08: service uses senderResolver, not whatsappConfig.phoneId() directly.
        // lenient() prevents UnnecessaryStubbingException in tests whose code paths exit
        // before reaching senderResolver.resolve (e.g. blank-mobile early-return tests).
        lenient().when(senderResolver.resolve(any(Audience.class), any())).thenReturn("testPhoneId");
        // ADR-028: pass null for cooldown repo — existing tests run with no-throttle mode.
        service = new WhatsappNotificationService(whatsAppService, whatsappConfig, new ObjectMapper(), "https://video.url", senderResolver, null);
        // ADR-028: service with wired cooldown repo for throttle tests.
        serviceWithCooldown = new WhatsappNotificationService(whatsAppService, whatsappConfig, new ObjectMapper(), "https://video.url", senderResolver, cooldownRepo);
    }

    private UserProfile customerProfile(String name, String mobile) {
        return new UserProfile(name, UserProfile.SignUpReason.BUY, "Address", "img", mobile, ProfileRoles.CUSTOMER);
    }

    private Order orderWithId(String id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }

    // ─── notifyDriverArrivedForPickup ────────────────────────────────────────

    @Test
    void notifyDriverArrivedForPickup_sendsPickupTemplateWithCorrectRecipientAndParams() throws IOException {
        Order order = orderWithId("order123");
        UserProfile customer = customerProfile("John Doe", "0821234567");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForPickup(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(eq("testPhoneId"), captor.capture());
        verify(callMock).execute();

        WhatsappTemplateRequest request = captor.getValue();
        assertEquals("+27821234567", request.getTo());
        assertEquals("driver_arrived_pickup", request.getTemplate().getName());
        var bodyComp = request.getTemplate().getComponents().get(0);
        assertEquals(WhatsappTemplateRequest.ComponentType.BODY, bodyComp.getType());
        assertEquals("John Doe", bodyComp.getParameters().get(0).getText());
        assertEquals("order123", bodyComp.getParameters().get(1).getText());

        var buttonComp = request.getTemplate().getComponents().get(1);
        assertEquals(WhatsappTemplateRequest.ComponentType.BUTTON, buttonComp.getType());
        assertEquals(WhatsappTemplateRequest.ButtonSubType.URL, buttonComp.getSub_type());
        assertEquals(0, buttonComp.getIndex());
        assertEquals("order123", buttonComp.getParameters().get(0).getText());
    }

    @Test
    void notifyDriverArrivedForPickup_normalizesZeroPrefixMobileNumber() throws IOException {
        Order order = orderWithId("order123");
        UserProfile customer = customerProfile("Jane", "0839991234");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForPickup(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        assertEquals("+27839991234", captor.getValue().getTo());
    }

    @Test
    void notifyDriverArrivedForPickup_usesFallbackNameWhenCustomerNameIsNull() throws IOException {
        Order order = orderWithId("order123");
        UserProfile customer = customerProfile(null, "+27821234567");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForPickup(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("Customer", bodyComp.getParameters().get(0).getText());
    }

    @Test
    void notifyDriverArrivedForPickup_handlesCustomerNameWithJsonSpecialCharacters() throws IOException {
        Order order = orderWithId("order123");
        UserProfile customer = customerProfile("John \"Danger\" O'Brien\\Jr", "0821234567");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        // Must not throw due to JSON injection
        assertDoesNotThrow(() -> service.notifyDriverArrivedForPickup(order, customer));
        verify(callMock).execute();

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("John \"Danger\" O'Brien\\Jr", bodyComp.getParameters().get(0).getText());
    }

    // ─── notifyDriverArrivedForDropOff ───────────────────────────────────────

    @Test
    void notifyDriverArrivedForDropOff_sendsDropOffTemplateWithCorrectRecipientAndParams() throws IOException {
        Order order = orderWithId("order456");
        UserProfile customer = customerProfile("Jane Doe", "0829876543");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForDropOff(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(eq("testPhoneId"), captor.capture());
        verify(callMock).execute();

        WhatsappTemplateRequest request = captor.getValue();
        assertEquals("+27829876543", request.getTo());
        assertEquals("driver_arrived_dropoff", request.getTemplate().getName());
        var bodyComp = request.getTemplate().getComponents().get(0);
        assertEquals(WhatsappTemplateRequest.ComponentType.BODY, bodyComp.getType());
        assertEquals("Jane Doe", bodyComp.getParameters().get(0).getText());
        assertEquals("order456", bodyComp.getParameters().get(1).getText());
    }

    @Test
    void notifyDriverArrivedForDropOff_normalizesZeroPrefixMobileNumber() throws IOException {
        Order order = orderWithId("order456");
        UserProfile customer = customerProfile("Jane", "0829876543");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForDropOff(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        assertEquals("+27829876543", captor.getValue().getTo());
    }

    @Test
    void notifyDriverArrivedForDropOff_usesFallbackNameWhenCustomerNameIsNull() throws IOException {
        Order order = orderWithId("order456");
        UserProfile customer = customerProfile(null, "+27829876543");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverArrivedForDropOff(order, customer);

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("Customer", bodyComp.getParameters().get(0).getText());
    }

    @Test
    void notifyDriverArrivedForDropOff_handlesCustomerNameWithJsonSpecialCharacters() throws IOException {
        Order order = orderWithId("order456");
        UserProfile customer = customerProfile("Jane \"The\" Smith\\Doe", "0829876543");
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        assertDoesNotThrow(() -> service.notifyDriverArrivedForDropOff(order, customer));
        verify(callMock).execute();

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("Jane \"The\" Smith\\Doe", bodyComp.getParameters().get(0).getText());
    }

    // ─── REQ-23: outbound reply uses the inbound line's audience/storeId ────────

    @SuppressWarnings("unchecked")
    @Test
    void sendMessage_twoArgOverload_resolvesCustomerAudienceForLegacyBehavior() throws IOException {
        Call<WhatsappTextResponse> textCallMock = mock(Call.class);
        when(whatsAppService.sendTextMessage(anyString(), any())).thenReturn(textCallMock);

        service.sendMessage("0821234567", "hello");

        verify(senderResolver).resolve(Audience.CUSTOMER, null);
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendMessage_audienceOverload_resolvesStoreAudienceWithStoreId_notCustomerDefault() throws IOException {
        Call<WhatsappTextResponse> textCallMock = mock(Call.class);
        when(whatsAppService.sendTextMessage(anyString(), any())).thenReturn(textCallMock);

        service.sendMessage("0821234567", "Hi from RxNova24", Audience.STORE, "store-123");

        ArgumentCaptor<WhatsappTextRequest> captor = ArgumentCaptor.forClass(WhatsappTextRequest.class);
        verify(senderResolver).resolve(Audience.STORE, "store-123");
        verify(senderResolver, never()).resolve(eq(Audience.CUSTOMER), any());
        verify(whatsAppService).sendTextMessage(eq("testPhoneId"), captor.capture());
        assertEquals("Hi from RxNova24", captor.getValue().getText().getBody());
    }

    @Test
    void sendLandingOptions_threeArgOverload_resolvesCustomerAudienceForLegacyBehavior() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.sendLandingOptions("0821234567", "Jane", customerProfile("Jane", "0821234567"));

        verify(senderResolver).resolve(Audience.CUSTOMER, null);
    }

    @Test
    void sendLandingOptions_audienceOverload_resolvesStoreAudienceWithStoreId_notCustomerDefault() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.sendLandingOptions("0821234567", "Jane", customerProfile("Jane", "0821234567"),
                Audience.STORE, "store-123");

        verify(senderResolver).resolve(Audience.STORE, "store-123");
        verify(senderResolver, never()).resolve(eq(Audience.CUSTOMER), any());
        verify(whatsAppService).sendMessage(eq("testPhoneId"), any());
    }

    // ─── REQ-24: per-store landing template ──────────────────────────────────

    @Test
    void sendLandingOptions_fiveArgOverload_usesGenericTemplateForLegacyBehavior() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.sendLandingOptions("0821234567", "Jane", customerProfile("Jane", "0821234567"),
                Audience.STORE, "store-123");

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        assertEquals("izinga_landing_options", captor.getValue().getTemplate().getName());
    }

    @Test
    void sendLandingOptions_blankTemplateName_fallsBackToGenericTemplate() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.sendLandingOptions("0821234567", "Jane", customerProfile("Jane", "0821234567"),
                Audience.STORE, "store-123", "  ");

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        assertEquals("izinga_landing_options", captor.getValue().getTemplate().getName());
    }

    @Test
    void sendLandingOptions_storeTemplateName_usesStoreSpecificTemplate_notGenericIzingaOne() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.sendLandingOptions("0821234567", "Jane", customerProfile("Jane", "0821234567"),
                Audience.STORE, "store-123", "rxnova24_landing_options");

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(eq("testPhoneId"), captor.capture());
        assertEquals("rxnova24_landing_options", captor.getValue().getTemplate().getName());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("Jane", bodyComp.getParameters().get(0).getText());
    }

    // ─── notifyDriverMissingBankDetails (BUG-3) ──────────────────────────────

    @Test
    void notifyDriverMissingBankDetails_blankMobile_returnsWithoutSending() {
        // Branch 1a: blank string — guard fires, early return, no outbound call
        assertDoesNotThrow(() -> service.notifyDriverMissingBankDetails("", "Sipho", "recipient-1"));
        verify(whatsAppService, never()).sendMessage(anyString(), any());

        // Branch 1b: null — isBlank() throws NPE inside try/catch, also no outbound call
        assertDoesNotThrow(() -> service.notifyDriverMissingBankDetails(null, "Sipho", "recipient-1"));
        verify(whatsAppService, never()).sendMessage(anyString(), any());
    }

    @Test
    void notifyDriverMissingBankDetails_validMobile_postsTemplate() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyDriverMissingBankDetails("0821234567", "Sipho", "messenger-id-1");

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(eq("testPhoneId"), captor.capture());
        verify(callMock).execute();

        WhatsappTemplateRequest request = captor.getValue();
        // 0-prefix normalised to +27
        assertEquals("+27821234567", request.getTo());
        assertEquals("driver_missing_bank_details", request.getTemplate().getName());
        assertEquals("en", request.getTemplate().getLanguage().getCode());
        var bodyComp = request.getTemplate().getComponents().get(0);
        assertEquals(WhatsappTemplateRequest.ComponentType.BODY, bodyComp.getType());
        assertEquals("Sipho", bodyComp.getParameters().get(0).getText());
        verify(senderResolver).resolve(Audience.DRIVER, null);
    }

    @Test
    void notifyDriverMissingBankDetails_driverNameWithJsonSpecialChars_sendsNotificationWithNamePreserved() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        assertDoesNotThrow(() -> service.notifyDriverMissingBankDetails("0821234567", "John \"Danger\" O'Brien\\Jr", "messenger-id-2"));
        verify(callMock).execute();

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("John \"Danger\" O'Brien\\Jr", bodyComp.getParameters().get(0).getText());
    }

    @Test
    void notifyDriverMissingBankDetails_senderThrows_doesNotRethrow() {
        when(whatsAppService.sendMessage(anyString(), any())).thenThrow(new RuntimeException("simulated network error"));
        assertDoesNotThrow(() -> service.notifyDriverMissingBankDetails("0821234567", "Sipho", "messenger-id-3"));
    }

    // ─── notifyStoreMissingBankDetails (GAP-7) ───────────────────────────────

    @Test
    void notifyStoreMissingBankDetails_blankMobile_returnsWithoutSending() {
        // Branch 1a: blank string
        assertDoesNotThrow(() -> service.notifyStoreMissingBankDetails("", "Acme Store", "store-1"));
        verify(whatsAppService, never()).sendMessage(anyString(), any());

        // Branch 1b: null — NPE caught, still no outbound call
        assertDoesNotThrow(() -> service.notifyStoreMissingBankDetails(null, "Acme Store", "store-1"));
        verify(whatsAppService, never()).sendMessage(anyString(), any());
    }

    @Test
    void notifyStoreMissingBankDetails_validMobile_postsTemplate() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        service.notifyStoreMissingBankDetails("0829876543", "Acme Store", "store-id-1");

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(eq("testPhoneId"), captor.capture());
        verify(callMock).execute();

        WhatsappTemplateRequest request = captor.getValue();
        // 0-prefix normalised to +27
        assertEquals("+27829876543", request.getTo());
        assertEquals("store_missing_bank_details", request.getTemplate().getName());
        assertEquals("en", request.getTemplate().getLanguage().getCode());
        var bodyComp = request.getTemplate().getComponents().get(0);
        assertEquals(WhatsappTemplateRequest.ComponentType.BODY, bodyComp.getType());
        assertEquals("Acme Store", bodyComp.getParameters().get(0).getText());

        // Store notification uses CUSTOMER audience (not DRIVER)
        verify(senderResolver).resolve(Audience.CUSTOMER, null);
    }

    @Test
    void notifyStoreMissingBankDetails_storeNameWithJsonSpecialChars_sendsNotificationWithNamePreserved() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        assertDoesNotThrow(() -> service.notifyStoreMissingBankDetails("0829876543", "Bob's \"BBQ\" Shop\\Ltd", "store-id-2"));
        verify(callMock).execute();

        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(anyString(), captor.capture());
        var bodyComp = captor.getValue().getTemplate().getComponents().get(0);
        assertEquals("Bob's \"BBQ\" Shop\\Ltd", bodyComp.getParameters().get(0).getText());
    }

    @Test
    void notifyStoreMissingBankDetails_senderThrows_doesNotRethrow() {
        when(whatsAppService.sendMessage(anyString(), any())).thenThrow(new RuntimeException("simulated network error"));
        assertDoesNotThrow(() -> service.notifyStoreMissingBankDetails("0829876543", "Acme Store", "store-id-3"));
    }

    // ─── sendShopOptions (ADR-029) ───────────────────────────────────────────

    /**
     * AC-09 (happy path): sendShopOptions sends the izinga_shop_options template using the
     * CUSTOMER audience line, and returns true on success.
     */
    @Test
    void sendShopOptions_happyPath_sendsIzingaShopOptionsTemplateAndReturnsNormally() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        boolean result = service.sendShopOptions("+27821234567", "Test User");

        assertTrue(result, "sendShopOptions must return true when the Meta API call succeeds");
        verify(senderResolver).resolve(Audience.CUSTOMER, null);
        ArgumentCaptor<WhatsappTemplateRequest> captor = ArgumentCaptor.forClass(WhatsappTemplateRequest.class);
        verify(whatsAppService).sendMessage(eq("testPhoneId"), captor.capture());
        assertEquals("izinga_shop_options", captor.getValue().getTemplate().getName());
        assertEquals("+27821234567", captor.getValue().getTo());
    }

    /**
     * AC-09 (IOException path): When the WhatsApp send call throws IOException (e.g. network
     * error, unapproved Meta template), sendShopOptions absorbs the exception, does NOT rethrow,
     * and returns false to signal the absorbed failure to the caller.
     * This is the designed degradation path.
     */
    @Test
    void sendShopOptions_whatsappSendThrowsIOException_absorbsExceptionAndDoesNotRethrow() throws IOException {
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);
        when(callMock.execute()).thenThrow(new IOException("Meta API: template not approved"));

        boolean result = service.sendShopOptions("+27821234567", "Test User");

        assertFalse(result, "sendShopOptions must return false when IOException is absorbed (AC-09 degraded-outcome signal)");
    }

    /**
     * Regression test (AC-09 DataAccessException path): proves that
     * {@code sendShopOptions} absorbs an unchecked {@code DataAccessException} thrown by
     * {@code senderResolver.resolve()} (e.g. MongoDB connectivity failure, timeout) and
     * returns {@code false} without propagating the exception to the agent.
     *
     * <p>This test was written as a failing sentinel while the defect was open (ADR-029 Defect 1).
     * The fix broadened the catch block from {@code IOException} to {@code Exception}, so
     * {@code DataAccessException} — which extends {@code RuntimeException}, not {@code IOException}
     * — is now absorbed. <strong>Narrowing the catch back to {@code IOException} will cause this
     * test to fail</strong>, confirming that the fix is still in place.</p>
     */
    @Test
    void sendShopOptions_senderResolverThrowsDataAccessException_absorbsExceptionAndDoesNotRethrow() {
        // Simulate MongoDB connectivity failure during sender resolution (e.g. timeout, unavailable)
        when(senderResolver.resolve(any(Audience.class), any()))
                .thenThrow(new DataAccessResourceFailureException("MongoDB timeout: senderResolver.resolve"));

        // AC-09 requires graceful degradation — the agent must NOT see this exception.
        // Currently FAILS: DataAccessException is not caught by catch (IOException e).
        // Fix: broaden catch to catch (Exception e) or add a separate catch (RuntimeException e) block.
        boolean result = service.sendShopOptions("+27821234567", "Test User");

        assertFalse(result,
                "AC-09: sendShopOptions must absorb DataAccessException from senderResolver.resolve() " +
                "and return false. The agent must never see this exception.");
    }

    /**
     * AC-09 (null mobileNumber): When mobileNumber is null, the null guard returns false
     * immediately (mirrors sibling sendCrimnalCheckConsent). No downstream call is made.
     */
    @Test
    void sendShopOptions_nullMobileNumber_doesNotThrowNullPointerException() {
        // null/blank guard: returns false immediately, no downstream call
        boolean result = service.sendShopOptions(null, null);

        assertFalse(result, "sendShopOptions must return false for null mobileNumber (null guard)");
        verify(senderResolver, never()).resolve(any(), any());
    }

    // ─── ADR-028: Throttle / acquireCooldown tests ───────────────────────────

    @Test
    void acquireCooldown_firstCall_insertsAndSendsNotification() throws IOException {
        // insert succeeds → cooldown acquired → notification is sent
        when(cooldownRepo.insert(any(PayoutNotificationCooldown.class)))
                .thenReturn(new PayoutNotificationCooldown("messenger-t1:DRIVER_MISSING_BANK", new Date()));
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        serviceWithCooldown.notifyDriverMissingBankDetails("0821234567", "Sipho", "messenger-t1");

        verify(cooldownRepo).insert(any(PayoutNotificationCooldown.class));
        verify(whatsAppService).sendMessage(anyString(), any());
    }

    @Test
    void acquireCooldown_secondCallWithinCooldown_suppressesNotification() {
        // insert throws DuplicateKeyException, existing record is fresh (<24h) → suppressed
        when(cooldownRepo.insert(any(PayoutNotificationCooldown.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        var freshRecord = new PayoutNotificationCooldown(
                "messenger-t2:DRIVER_MISSING_BANK",
                new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) // 1h ago — fresh
        );
        when(cooldownRepo.findById("messenger-t2:DRIVER_MISSING_BANK"))
                .thenReturn(Optional.of(freshRecord));

        serviceWithCooldown.notifyDriverMissingBankDetails("0821234567", "Sipho", "messenger-t2");

        // Notification must NOT be sent
        verify(whatsAppService, never()).sendMessage(anyString(), any());
    }

    @Test
    void acquireCooldown_staleCooldownRecord_overwritesAndSendsNotification() throws IOException {
        // insert throws DuplicateKeyException, existing record is stale (≥24h) → overwrite → send
        when(cooldownRepo.insert(any(PayoutNotificationCooldown.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        var staleRecord = new PayoutNotificationCooldown(
                "messenger-t3:DRIVER_MISSING_BANK",
                new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25)) // 25h ago — stale
        );
        when(cooldownRepo.findById("messenger-t3:DRIVER_MISSING_BANK"))
                .thenReturn(Optional.of(staleRecord));
        when(cooldownRepo.save(any(PayoutNotificationCooldown.class)))
                .thenReturn(new PayoutNotificationCooldown("messenger-t3:DRIVER_MISSING_BANK", new Date()));
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        serviceWithCooldown.notifyDriverMissingBankDetails("0821234567", "Sipho", "messenger-t3");

        verify(cooldownRepo).save(any(PayoutNotificationCooldown.class));
        verify(whatsAppService).sendMessage(anyString(), any());
    }

    @Test
    void acquireCooldown_twoDistinctRecipients_bothNotificationsAreSent() throws IOException {
        // Two different recipients → distinct cooldown keys → both inserts succeed → both send
        when(cooldownRepo.insert(any(PayoutNotificationCooldown.class)))
                .thenReturn(new PayoutNotificationCooldown("", new Date()));
        when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        serviceWithCooldown.notifyDriverMissingBankDetails("0821234567", "Sipho", "messenger-t4a");
        serviceWithCooldown.notifyDriverMissingBankDetails("0829876543", "Thabo", "messenger-t4b");

        verify(whatsAppService, times(2)).sendMessage(anyString(), any());
    }

    @Test
    void acquireCooldown_sameRecipientTwice_secondCallIsSuppressedWhenCooldownActive() {
        // Same recipient, second call within 24h → suppressed
        // First call: insert succeeds
        when(cooldownRepo.insert(any(PayoutNotificationCooldown.class)))
                .thenReturn(new PayoutNotificationCooldown("messenger-t5:DRIVER_MISSING_BANK", new Date()))
                .thenThrow(new DuplicateKeyException("dup"));
        var freshRecord = new PayoutNotificationCooldown(
                "messenger-t5:DRIVER_MISSING_BANK",
                new Date() // just created
        );
        when(cooldownRepo.findById("messenger-t5:DRIVER_MISSING_BANK"))
                .thenReturn(Optional.of(freshRecord));
        lenient().when(whatsAppService.sendMessage(anyString(), any())).thenReturn(callMock);

        serviceWithCooldown.notifyDriverMissingBankDetails("0821234567", "Sipho", "messenger-t5");
        serviceWithCooldown.notifyDriverMissingBankDetails("0821234567", "Sipho", "messenger-t5");

        // Only one notification should have been sent
        verify(whatsAppService, times(1)).sendMessage(anyString(), any());
    }
}
