package io.curiousoft.izinga.ordermanagement.stores;

import com.google.firebase.auth.FirebaseAuth;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.AudienceViolationException;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SEC-MCP-01 (CORRECTION 1) / SEC-MCP-02 / AMD-01: Unit tests for StoreService MCP-surface security.
 *
 * Covers:
 * - find(): MCP path returns masked bank accountId, REST path returns real accountId
 * - find(): profileRepo.save() is never called with a masked accountId
 * - find(): MCP request without valid audience throws AudienceViolationException
 * - find(): ADMIN audience passes gate and bank accountId is still masked (REQ-A10)
 * - findByOwner(): MCP path (ADMIN audience) masks bank accountId, REST path does not
 * - findByOwner(): null audience on MCP path throws
 * - findByOwner(): STORE audience with valid permit passes
 * - findByOwner(): STORE audience without permit throws
 * - findByOwner(): STORE audience with ownerId mismatch throws
 * - findByOwner(): ADMIN audience passes without ownership check (REQ-A8)
 * - findByOwner(): CUSTOMER audience is now hard-rejected (REQ-A8)
 * - findByOwner(): DRIVER audience is now hard-rejected (REQ-A8)
 * - findStores(): MCP path requires authenticated audience
 * - findStores(): ADMIN audience passes gate (REQ-A10)
 */
@ExtendWith(MockitoExtension.class)
public class StoreServiceBankMaskingTest {

    private static final String MAIN_PAY_ACCOUNT = "main-pay-account-id";
    private static final String REAL_BANK_ACCOUNT = "real-bank-account-id";
    private static final String STORE_ID = "store-abc";
    private static final String OWNER_ID = "owner-user-id";
    private static final String CALLER_PHONE = "+27812345678";

    @Mock private StoreRepository storeRepository;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private ReferralCodeService referralCodeService;
    @Mock private StoreAgreementAuditRepository storeAgreementAuditRepository;
    @Mock private StoreTierChangeAuditRepository storeTierChangeAuditRepository;
    @Mock private FirebaseAuth firebaseAuth;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    private StoreService storeService;

    @BeforeEach
    public void setUp() {
        StoreScopeContext.clear();
        storeService = new StoreService(
                storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, 0.0,
                applicationEventPublisher, referralCodeService,
                storeAgreementAuditRepository, storeTierChangeAuditRepository,
                firebaseAuth, agentIdentityAuditService);
    }

    @AfterEach
    public void tearDown() {
        StoreScopeContext.clear();
        io.curiousoft.izinga.commons.security.CallerIdentityContext.clear();
    }

    // ── find(): bank masking ─────────────────────────────────────────────────

    @Test
    public void find_mcpRequest_bankAccountIdMasked() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        StoreProfile result = storeService.find(STORE_ID);
        assertEquals(MAIN_PAY_ACCOUNT, result.getBank().getAccountId(),
                "MCP path must mask bank accountId");
    }

    @Test
    public void find_restRequest_bankAccountIdNotMasked() {
        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        StoreProfile result = storeService.find(STORE_ID);
        assertEquals(REAL_BANK_ACCOUNT, result.getBank().getAccountId(),
                "REST path must NOT mask bank accountId");
    }

    @Test
    public void find_mcpRequest_profileRepoSaveNeverCalledWithMaskedAccount() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setCategories(List.of(new Category("cat1", "Cat", "", true)));
        store.setBusinessHours(List.of());
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        storeService.find(STORE_ID);

        verify(storeRepository, never()).save(argThat(s ->
            MAIN_PAY_ACCOUNT.equals(s.getBank() != null ? s.getBank().getAccountId() : null)));
    }

    @Test
    public void find_mcpRequest_noAudience_throwsAudienceViolation() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);

        // No stubs needed: audience gate fires before findById is called
        assertThrows(AudienceViolationException.class, () ->
                storeService.find(STORE_ID));
    }

    /**
     * FIX-01 (Code Review): Verifies that when businessHours == null (seeding branch),
     * profileRepo.save() receives the REAL bank accountId (never the masked value), and that
     * the returned store exposes the masked accountId to MCP callers.
     *
     * StoreService.find() mutates the same object after save():
     *   store.getBank().setAccountId(mainPayAccount)
     * A naive argThat on the saved argument would see the already-mutated (masked) value and
     * give a false-negative. We capture accountId at call time via AtomicReference to get the
     * value that was actually passed to save().
     */
    @Test
    public void find_mcpRequest_businessHoursNull_saveCalledWithRealAccountId_returnedValueMasked() {
        // Arrange: MCP request with ADMIN audience (permitted for find())
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        // Store with businessHours = null (triggers seeding branch + save) and empty categories
        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setBusinessHours(null);
        // categories defaults to emptyList() — the find() seeding branch will call buildDefaultCategories()

        // Capture the accountId passed to save() BEFORE find() mutates the object
        AtomicReference<String> savedAccountId = new AtomicReference<>();
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> {
            StoreProfile s = inv.getArgument(0);
            savedAccountId.set(s.getBank().getAccountId());
            return s;
        });

        // Act
        StoreProfile result = storeService.find(STORE_ID);

        // Assert: save() was called exactly once with the REAL account (not masked)
        verify(storeRepository, times(1)).save(any());
        assertEquals(REAL_BANK_ACCOUNT, savedAccountId.get(),
                "profileRepo.save() must receive the real bank accountId, never the masked value");
        // Assert: returned store has the masked accountId (MCP surface protection)
        assertEquals(MAIN_PAY_ACCOUNT, result.getBank().getAccountId(),
                "MCP path must return masked bank accountId after seeding save");
    }

    // ── findByOwner(): bank masking ──────────────────────────────────────────

    @Test
    public void findByOwner_mcpRequest_bankAccountIdMasked() {
        // AMD-01 (REQ-A8): ADMIN audience used here because CUSTOMER is now hard-rejected
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setOwnerId(OWNER_ID);
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
        assertEquals(MAIN_PAY_ACCOUNT, result.get(0).getBank().getAccountId(),
                "MCP path must mask bank accountId");
    }

    @Test
    public void findByOwner_restRequest_bankAccountIdNotMasked() {
        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setOwnerId(OWNER_ID);
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
        assertEquals(REAL_BANK_ACCOUNT, result.get(0).getBank().getAccountId(),
                "REST path must NOT mask bank accountId");
    }

    // ── findByOwner(): null audience on MCP path → throw ────────────────────

    @Test
    public void findByOwner_mcpRequest_nullAudience_throws() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);

        assertThrows(CallerIdentityViolationException.class, () ->
                storeService.findByOwner(OWNER_ID));
    }

    // ── findByOwner(): STORE audience with valid permit passes ───────────────

    @Test
    public void findByOwner_storeAudience_validPermit_passes() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setPermittedStoreId(STORE_ID);

        StoreProfile permitted = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        permitted.setOwnerId(OWNER_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(permitted));
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(permitted));

        List<StoreProfile> result = storeService.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
    }

    // ── findByOwner(): STORE audience without permit → throw ────────────────

    @Test
    public void findByOwner_storeAudience_noPermit_throws() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.STORE);

        assertThrows(CallerIdentityViolationException.class, () ->
                storeService.findByOwner(OWNER_ID));
    }

    // ── findByOwner(): STORE audience with ownerId mismatch → throw ──────────

    @Test
    public void findByOwner_storeAudience_ownerMismatch_throws() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setPermittedStoreId(STORE_ID);

        StoreProfile permitted = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        permitted.setOwnerId("different-owner-id");
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(permitted));

        assertThrows(CallerIdentityViolationException.class, () ->
                storeService.findByOwner(OWNER_ID));
    }

    // ── findStores(): MCP requires audience ─────────────────────────────────

    @Test
    public void findStores_mcpRequest_noAudience_throwsAudienceViolation() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);

        // No stub needed: audience gate fires before findAll is called
        assertThrows(AudienceViolationException.class, () ->
                storeService.findStores(null, null, null));
    }

    @Test
    public void findStores_mcpRequest_withAudience_passes() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        when(storeRepository.findAll()).thenReturn(Collections.emptyList());
        storeService.findStores(null, null, null); // must not throw
    }

    @Test
    public void findStores_restRequest_noAudience_passes() {
        when(storeRepository.findAll()).thenReturn(Collections.emptyList());
        storeService.findStores(null, null, null); // must not throw
    }

    /**
     * NOTE-01 (FIX-01 regression guard): findStores() bank masking is UNCONDITIONAL —
     * identical to develop behaviour. A REST caller (isMcpRequest == false) must also
     * receive the masked accountId, not the real one.
     */
    @Test
    public void findStores_restRequest_bankAccountIdMasked() {
        // No MCP flag set — IS_MCP_REQUEST == false
        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        when(storeRepository.findAll()).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findStores(null, null, null);

        assertFalse(result.isEmpty(), "Expected at least one store in results");
        assertEquals(MAIN_PAY_ACCOUNT, result.get(0).getBank().getAccountId(),
                "findStores() must mask bank accountId unconditionally (REST path)");
    }

    /**
     * NOTE-01 (FIX-01 regression guard): findStores() bank masking is UNCONDITIONAL —
     * identical to develop behaviour. An MCP caller with valid audience must also
     * receive the masked accountId.
     */
    @Test
    public void findStores_mcpRequest_bankAccountIdMasked() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        when(storeRepository.findAll()).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findStores(null, null, null);

        assertFalse(result.isEmpty(), "Expected at least one store in results");
        assertEquals(MAIN_PAY_ACCOUNT, result.get(0).getBank().getAccountId(),
                "findStores() must mask bank accountId unconditionally (MCP path)");
    }

    // ── AMD-01: findByOwner() ADMIN / CUSTOMER / DRIVER audience gates ───────

    /**
     * REQ-A8: ADMIN audience must pass findByOwner() without any ownership check.
     */
    @Test
    public void findByOwner_adminAudience_passes() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setOwnerId(OWNER_ID);
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
    }

    /**
     * REQ-A8: CUSTOMER audience must now be hard-rejected by findByOwner().
     * The prior "resolved-caller-phone == ownerId" path was removed in AMD-01.
     */
    @Test
    public void findByOwner_customerAudience_nowRejects() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        assertThrows(Exception.class, () ->
                storeService.findByOwner(OWNER_ID));
    }

    /**
     * REQ-A8: DRIVER audience must now be hard-rejected by findByOwner().
     */
    @Test
    public void findByOwner_driverAudience_nowRejects() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.DRIVER);

        assertThrows(Exception.class, () ->
                storeService.findByOwner(OWNER_ID));
    }

    // ── AMD-01: findStores() ADMIN audience gate ─────────────────────────────

    /**
     * REQ-A10: ADMIN audience must pass the findStores() audience gate.
     */
    @Test
    public void findStores_adminAudience_passes() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);
        when(storeRepository.findAll()).thenReturn(Collections.emptyList());

        storeService.findStores(null, null, null); // must not throw
    }

    // ── AMD-01: find() ADMIN audience gate with bank masking ─────────────────

    /**
     * REQ-A10: ADMIN audience must pass the find() audience gate; bank masking still applies.
     */
    @Test
    public void find_adminAudience_passes_withBankMasking() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        StoreProfile result = storeService.find(STORE_ID);
        assertEquals(MAIN_PAY_ACCOUNT, result.getBank().getAccountId(),
                "ADMIN audience must still receive masked bank accountId");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private StoreProfile storeWithBank(String storeId, String bankAccountId) {
        Bank bank = new Bank();
        bank.setAccountId(bankAccountId);
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "Test Store", "test-store", "1 Test St",
                "https://img.test/store.png", "0812345678",
                new ArrayList<>(), new ArrayList<>(),
                OWNER_ID, bank
        );
        store.setId(storeId);
        return store;
    }
}
