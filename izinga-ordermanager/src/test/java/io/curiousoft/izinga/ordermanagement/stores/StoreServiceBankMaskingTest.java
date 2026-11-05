package io.curiousoft.izinga.ordermanagement.stores;

import com.google.firebase.auth.FirebaseAuth;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
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
import static org.mockito.Mockito.*;

/**
 * ADR-026 Phase 4: AC-04 service-layer ungated tests for StoreService.
 *
 * <p>After Phase 4 migration, all three store-query @Tool gates (audience validation,
 * store-scope validation, ownership enforcement) have been moved from {@link StoreService}
 * into {@link io.curiousoft.izinga.ordermanagement.mcp.StoreMcpController}.
 * Bank-account masking has also moved to the controller layer (REQ-08).
 *
 * <p>These tests verify:
 * - AC-04: calling the service with any MCP context, including gate-triggering contexts,
 *   does NOT produce a {@link io.curiousoft.izinga.commons.security.CallerIdentityViolationException}
 *   or {@link io.curiousoft.izinga.ordermanagement.security.AudienceViolationException}.
 * - REQ-08 (service layer): the service does NOT mask bank accountId — that is the
 *   controller's responsibility. Gate enforcement and masking tests live in
 *   {@link io.curiousoft.izinga.ordermanagement.mcp.StoreMcpControllerTest}.
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

    private StoreService storeService;

    @BeforeEach
    public void setUp() {
        StoreScopeContext.clear();
        // ADR-026 Phase 4: agentIdentityAuditService removed from StoreService constructor
        storeService = new StoreService(
                storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, 0.0,
                applicationEventPublisher, referralCodeService,
                storeAgreementAuditRepository, storeTierChangeAuditRepository,
                firebaseAuth);
    }

    @AfterEach
    public void tearDown() {
        StoreScopeContext.clear();
        io.curiousoft.izinga.commons.security.CallerIdentityContext.clear();
    }

    // ── find(): AC-04 / no masking in service ──────────────────────────────────────────

    /**
     * AC-04: find() must not throw AudienceViolationException on MCP + null audience.
     * Gate was moved to StoreMcpController in Phase 4.
     */
    @Test
    public void find_mcpNullAudience_serviceDoesNotGate() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        // No audience — previously threw AudienceViolationException from service
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(storeWithBank(STORE_ID, REAL_BANK_ACCOUNT)));

        assertDoesNotThrow(() -> storeService.find(STORE_ID),
                "ADR-026 Phase 4: audience gate moved to StoreMcpController; service must not gate");
    }

    /**
     * REQ-08 (service layer): find() must NOT mask bank accountId on MCP requests.
     * Masking is the controller's responsibility (applied after the service call).
     */
    @Test
    public void find_mcpRequest_serviceDoesNotMask() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(storeWithBank(STORE_ID, REAL_BANK_ACCOUNT)));

        StoreProfile result = storeService.find(STORE_ID);
        assertEquals(REAL_BANK_ACCOUNT, result.getBank().getAccountId(),
                "Service must NOT mask bank accountId — masking belongs in StoreMcpController");
    }

    @Test
    public void find_restRequest_bankAccountIdNotMasked() {
        // REST path has never masked — still correct
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(storeWithBank(STORE_ID, REAL_BANK_ACCOUNT)));

        StoreProfile result = storeService.find(STORE_ID);
        assertEquals(REAL_BANK_ACCOUNT, result.getBank().getAccountId(),
                "REST path must not mask bank accountId");
    }

    /**
     * FIX-01 (regression guard): when businessHours == null, the service calls
     * profileRepo.save() during default-hours seeding. The save must receive the REAL
     * bank accountId — there is no masking anywhere in the service.
     *
     * We capture the accountId at call time via AtomicReference to avoid the
     * mutation-after-save false-negative.
     */
    @Test
    public void find_businessHoursNull_saveCalledWithRealAccountId_returnedValueNotMasked() {
        // Arrange: MCP request (masking logic is now in the controller, not the service)
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setBusinessHours(null); // triggers seeding branch + save

        AtomicReference<String> savedAccountId = new AtomicReference<>();
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> {
            StoreProfile s = inv.getArgument(0);
            savedAccountId.set(s.getBank().getAccountId());
            return s;
        });

        StoreProfile result = storeService.find(STORE_ID);

        // save() must receive the REAL account (service never masks)
        verify(storeRepository, times(1)).save(any());
        assertEquals(REAL_BANK_ACCOUNT, savedAccountId.get(),
                "profileRepo.save() must receive the real bank accountId (service does not mask)");
        // returned value must also be the real account (service does not mask)
        assertEquals(REAL_BANK_ACCOUNT, result.getBank().getAccountId(),
                "Service must return the real bank accountId after seeding save (masking is controller's job)");
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

        // The service never masks, so save() should never be called with a masked accountId.
        verify(storeRepository, never()).save(argThat(s ->
            MAIN_PAY_ACCOUNT.equals(s.getBank() != null ? s.getBank().getAccountId() : null)));
    }

    // ── findByOwner(): AC-04 / no masking in service ──────────────────────────────────

    /**
     * AC-04: findByOwner() must not throw on MCP + null audience.
     * Gate was moved to StoreMcpController in Phase 4.
     */
    @Test
    public void findByOwner_mcpNullAudience_serviceDoesNotGate() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        // No audience — previously threw CallerIdentityViolationException from service
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of());

        assertDoesNotThrow(() -> storeService.findByOwner(OWNER_ID),
                "ADR-026 Phase 4: null-audience gate moved to StoreMcpController; service must not gate");
    }

    /**
     * AC-04: findByOwner() must not throw for STORE audience with no permit.
     * Store-scope gate was moved to StoreMcpController in Phase 4.
     */
    @Test
    public void findByOwner_mcpStoreAudience_noPermit_serviceDoesNotGate() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.STORE);
        // No permittedStoreId — previously threw CallerIdentityViolationException from service
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of());

        assertDoesNotThrow(() -> storeService.findByOwner(OWNER_ID),
                "ADR-026 Phase 4: store-scope gate moved to controller; service must not gate");
    }

    /**
     * REQ-08 (service layer): findByOwner() must NOT mask bank accountId on MCP requests.
     */
    @Test
    public void findByOwner_mcpRequest_serviceDoesNotMask() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setOwnerId(OWNER_ID);
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
        assertEquals(REAL_BANK_ACCOUNT, result.get(0).getBank().getAccountId(),
                "Service must NOT mask bank accountId — masking belongs in StoreMcpController");
    }

    @Test
    public void findByOwner_restRequest_bankAccountIdNotMasked() {
        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        store.setOwnerId(OWNER_ID);
        when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
        assertEquals(REAL_BANK_ACCOUNT, result.get(0).getBank().getAccountId(),
                "REST path must not mask bank accountId");
    }

    /**
     * AC-04: findByOwner() must pass for any audience (ADMIN/STORE/CUSTOMER/DRIVER).
     * Service now has no audience gate — it just calls the repo.
     */
    @Test
    public void findByOwner_anyAudience_serviceDoesNotGate() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);

        for (Audience audience : new Audience[]{Audience.ADMIN, Audience.STORE,
                Audience.CUSTOMER, Audience.DRIVER}) {
            StoreScopeContext.setAudience(audience);
            when(storeRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of());
            assertDoesNotThrow(() -> storeService.findByOwner(OWNER_ID),
                    "Service must not gate audience " + audience);
        }
    }

    // ── findStores(): AC-04 / no masking in service ───────────────────────────────────

    /**
     * AC-04: findStores() must not throw on MCP + null audience.
     * Gate was moved to StoreMcpController in Phase 4.
     */
    @Test
    public void findStores_mcpNullAudience_serviceDoesNotGate() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        // No audience — previously threw AudienceViolationException from service
        when(storeRepository.findAll()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> storeService.findStores(null, null, null),
                "ADR-026 Phase 4: audience gate moved to StoreMcpController; service must not gate");
    }

    @Test
    public void findStores_mcpRequest_withAudience_passes() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        when(storeRepository.findAll()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> storeService.findStores(null, null, null));
    }

    @Test
    public void findStores_restRequest_noAudience_passes() {
        when(storeRepository.findAll()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> storeService.findStores(null, null, null));
    }

    /**
     * REQ-08 (service layer): findStores() must NOT mask bank accountId.
     * Masking was unconditional in the original service but is now the controller's job.
     */
    @Test
    public void findStores_serviceDoesNotMask() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        StoreProfile store = storeWithBank(STORE_ID, REAL_BANK_ACCOUNT);
        when(storeRepository.findAll()).thenReturn(List.of(store));

        List<StoreProfile> result = storeService.findStores(null, null, null);
        assertFalse(result.isEmpty());
        assertEquals(REAL_BANK_ACCOUNT, result.get(0).getBank().getAccountId(),
                "Service must NOT mask bank accountId — masking belongs in StoreMcpController");
    }

    @Test
    public void findStores_adminAudience_passes() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);
        when(storeRepository.findAll()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> storeService.findStores(null, null, null));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────

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
