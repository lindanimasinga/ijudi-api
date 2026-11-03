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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SEC-MCP-01 (CORRECTION 1) / SEC-MCP-02: Unit tests for StoreService MCP-surface security.
 *
 * Covers:
 * - find(): MCP path returns masked bank accountId, REST path returns real accountId
 * - find(): profileRepo.save() is never called with a masked accountId
 * - find(): MCP request without valid audience throws AudienceViolationException
 * - findByOwner(): MCP path masks bank accountId, REST path does not
 * - findByOwner(): null audience on MCP path throws
 * - findByOwner(): STORE audience with valid permit passes
 * - findByOwner(): STORE audience without permit throws
 * - findByOwner(): non-STORE audience with matching owner passes
 * - findByOwner(): non-STORE audience with mismatching owner throws
 * - findStores(): MCP path requires authenticated audience
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
    }

    // ── find(): bank masking ─────────────────────────────────────────────────

    @Test
    public void find_mcpRequest_bankAccountIdMasked() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.find(STORE_ID);
        assertEquals(MAIN_PAY_ACCOUNT, result.getBank().getAccountId(),
                "MCP path must mask bank accountId");
    }

    @Test
    public void find_restRequest_bankAccountIdNotMasked() {
        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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

    // ── findByOwner(): bank masking ──────────────────────────────────────────

    @Test
    public void findByOwner_mcpRequest_bankAccountIdMasked() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setOwnerId(OWNER_ID);
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(store));
        UserProfile caller = new UserProfile("Caller", UserProfile.SignUpReason.BUY,
                "1 St", "https://img.test/1.png", CALLER_PHONE, ProfileRoles.CUSTOMER);
        caller.setId(OWNER_ID);
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(caller);

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

    // ── findByOwner(): non-STORE, caller owns owner → passes ────────────────

    @Test
    public void findByOwner_customerAudience_callerOwnsId_passes() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        UserProfile caller = new UserProfile("Caller", UserProfile.SignUpReason.BUY,
                "1 St", "https://img.test/1.png", CALLER_PHONE, ProfileRoles.CUSTOMER);
        caller.setId(OWNER_ID);
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(caller);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setOwnerId(OWNER_ID);
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
    }

    // ── findByOwner(): non-STORE, caller does NOT own → throw ───────────────

    @Test
    public void findByOwner_customerAudience_callerNotOwner_throws() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        UserProfile caller = new UserProfile("Caller", UserProfile.SignUpReason.BUY,
                "1 St", "https://img.test/1.png", CALLER_PHONE, ProfileRoles.CUSTOMER);
        caller.setId("some-other-id");
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(caller);

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
