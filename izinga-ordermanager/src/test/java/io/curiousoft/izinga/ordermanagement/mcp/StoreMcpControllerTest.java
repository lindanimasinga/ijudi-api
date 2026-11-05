package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.Bank;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.AudienceViolationException;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ADR-026 Phase 4: Unit tests for StoreMcpController gate enforcement and bank masking.
 *
 * <p>Coverage:
 * - find_store_or_shops_by_id: Gate 1 scope pass-through (no locked store); Gate 2
 *   null-audience AudienceViolationException; CUSTOMER/ADMIN passes; bank masked on MCP.
 * - find_stores_by_owner: null-audience CallerIdentityViolationException + audit;
 *   STORE + no permit throws + audit; STORE + valid permit passes + masked;
 *   STORE + owner mismatch throws + audit; ADMIN passes + masked;
 *   CUSTOMER/DRIVER throws + audit; non-MCP no gate, no masking.
 * - find_stores: null-audience AudienceViolationException; CUSTOMER passes;
 *   bank masked unconditionally; markUp set unconditionally.
 */
@ExtendWith(MockitoExtension.class)
public class StoreMcpControllerTest {

    @Mock private StoreService storeService;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    private StoreMcpController sut;

    private static final String MAIN_PAY_ACCOUNT = "main-pay-account-id";
    private static final double MARKUP           = 0.05;
    private static final String REAL_ACCOUNT     = "real-bank-account-id";
    private static final String STORE_ID         = "store-abc";
    private static final String OWNER_ID         = "owner-user-id";
    private static final String CALLER_PHONE     = "+27812345678";

    @BeforeEach
    public void setUp() {
        sut = new StoreMcpController(storeService, agentIdentityAuditService,
                MAIN_PAY_ACCOUNT, MARKUP);
        CallerIdentityContext.clear();
        StoreScopeContext.clear();
    }

    @AfterEach
    public void tearDown() {
        CallerIdentityContext.clear();
        StoreScopeContext.clear();
    }

    // ========== find_store_or_shops_by_id =================================================

    @Test
    public void findStore_mcpNullAudience_throwsAudienceViolation() {
        // Gate 2: SEC-MCP-02 — null audience on MCP request → AudienceViolationException
        // Gate 1 (StoreScopeValidator.validate): permittedStoreId == null → scope is open, passes
        CallerIdentityContext.setMcpRequest(true);
        // No audience set, no permittedStoreId → Gate 1 passes, Gate 2 throws

        assertThrows(AudienceViolationException.class, () -> sut.findStore(STORE_ID));
        // Gate fires before service call
        verifyNoInteractions(storeService);
    }

    @Test
    public void findStore_mcpCustomerAudience_passes_bankMasked() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        StoreProfile store = storeWithBank(REAL_ACCOUNT);
        when(storeService.find(STORE_ID)).thenReturn(store);

        StoreProfile result = sut.findStore(STORE_ID);
        assertNotNull(result);
        assertEquals(MAIN_PAY_ACCOUNT, result.getBank().getAccountId(),
                "MCP path must mask bank accountId");
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findStore_mcpAdminAudience_passes_bankMasked() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        StoreProfile store = storeWithBank(REAL_ACCOUNT);
        when(storeService.find(STORE_ID)).thenReturn(store);

        StoreProfile result = sut.findStore(STORE_ID);
        assertEquals(MAIN_PAY_ACCOUNT, result.getBank().getAccountId(),
                "ADMIN audience must still receive masked bank accountId");
    }

    @Test
    public void findStore_nonMcp_passes_bankNotMasked() {
        // Non-MCP: CallerIdentityContext.isMcpRequest() == false → both gates skipped
        StoreProfile store = storeWithBank(REAL_ACCOUNT);
        when(storeService.find(STORE_ID)).thenReturn(store);

        StoreProfile result = sut.findStore(STORE_ID);
        assertNotNull(result);
        assertEquals(REAL_ACCOUNT, result.getBank().getAccountId(),
                "REST path must NOT mask bank accountId");
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findStore_mcpCustomerAudience_nullBankStore_noNullPointer() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "Test", "test", "1 St", "img",
                "0812345678", new ArrayList<>(), new ArrayList<>(), OWNER_ID, null);
        store.setId(STORE_ID);
        when(storeService.find(STORE_ID)).thenReturn(store);

        // Must not throw NullPointerException when bank is null
        assertDoesNotThrow(() -> sut.findStore(STORE_ID));
    }

    // ========== find_stores_by_owner ======================================================

    @Test
    public void findByOwner_mcpNullAudience_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        CallerIdentityViolationException ex = assertThrows(
                CallerIdentityViolationException.class,
                () -> sut.findByOwner(OWNER_ID));

        assertEquals(StoreMcpController.UNAUTHENTICATED_MESSAGE, ex.getMessage());
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OWNER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_stores_by_owner"), eq("UNKNOWN"), isNull(), isNull());
        verifyNoInteractions(storeService);
    }

    @Test
    public void findByOwner_mcpStoreAudience_noPermit_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.STORE.name());
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        // No permittedStoreId set

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findByOwner(OWNER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OWNER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_stores_by_owner"), eq("STORE"), isNull(), isNull());
        verifyNoInteractions(storeService);
    }

    @Test
    public void findByOwner_mcpStoreAudience_validPermit_passes_bankMasked() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.STORE.name());
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        StoreScopeContext.setPermittedStoreId(STORE_ID);

        StoreProfile permitted = storeWithBank(REAL_ACCOUNT);
        permitted.setOwnerId(OWNER_ID);
        when(storeService.find(STORE_ID)).thenReturn(permitted);

        StoreProfile resultStore = storeWithBank(REAL_ACCOUNT);
        when(storeService.findByOwner(OWNER_ID)).thenReturn(List.of(resultStore));

        List<StoreProfile> result = sut.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
        assertEquals(MAIN_PAY_ACCOUNT, result.get(0).getBank().getAccountId(),
                "MCP path must mask bank accountId");
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findByOwner_mcpStoreAudience_ownerMismatch_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.STORE.name());
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        StoreScopeContext.setPermittedStoreId(STORE_ID);

        StoreProfile permitted = storeWithBank(REAL_ACCOUNT);
        permitted.setOwnerId("different-owner");
        when(storeService.find(STORE_ID)).thenReturn(permitted);

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findByOwner(OWNER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OWNER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_stores_by_owner"), eq("STORE"), isNull(), isNull());
    }

    @Test
    public void findByOwner_mcpAdminAudience_anyOwner_passes_bankMasked() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.ADMIN.name());
        StoreScopeContext.setAudience(Audience.ADMIN);

        StoreProfile store = storeWithBank(REAL_ACCOUNT);
        when(storeService.findByOwner(OWNER_ID)).thenReturn(List.of(store));

        List<StoreProfile> result = sut.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
        assertEquals(MAIN_PAY_ACCOUNT, result.get(0).getBank().getAccountId(),
                "ADMIN MCP path must mask bank accountId");
        // No ownership check for ADMIN — service called once for the actual query
        verify(storeService, never()).find(any());
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    public void findByOwner_mcpCustomerAudience_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findByOwner(OWNER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OWNER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_stores_by_owner"), eq("CUSTOMER"), isNull(), isNull());
        verifyNoInteractions(storeService);
    }

    @Test
    public void findByOwner_mcpDriverAudience_throwsAndAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.DRIVER.name());
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        assertThrows(CallerIdentityViolationException.class,
                () -> sut.findByOwner(OWNER_ID));
        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OWNER_ID),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_stores_by_owner"), eq("DRIVER"), isNull(), isNull());
        verifyNoInteractions(storeService);
    }

    @Test
    public void findByOwner_nonMcp_noGate_noBankMasking() {
        StoreProfile store = storeWithBank(REAL_ACCOUNT);
        when(storeService.findByOwner(OWNER_ID)).thenReturn(List.of(store));

        List<StoreProfile> result = sut.findByOwner(OWNER_ID);
        assertFalse(result.isEmpty());
        // REQ-08: masking is MCP-conditional — non-MCP returns real account
        assertEquals(REAL_ACCOUNT, result.get(0).getBank().getAccountId(),
                "Non-MCP path must NOT mask bank accountId");
        verifyNoInteractions(agentIdentityAuditService);
    }

    // ========== find_stores ===============================================================

    @Test
    public void findStores_mcpNullAudience_throwsAudienceViolation() {
        CallerIdentityContext.setMcpRequest(true);
        // No audience set

        assertThrows(AudienceViolationException.class,
                () -> sut.findStores(null, null, null));
        verifyNoInteractions(storeService);
    }

    @Test
    public void findStores_mcpCustomerAudience_passes_bankMaskedAndMarkupSet() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        StoreProfile store = spy(storeWithBank(REAL_ACCOUNT));
        when(storeService.findStores(isNull(), isNull(), isNull())).thenReturn(List.of(store));

        List<StoreProfile> result = sut.findStores(null, null, null);
        assertFalse(result.isEmpty());
        assertEquals(MAIN_PAY_ACCOUNT, result.get(0).getBank().getAccountId(),
                "MCP findStores must mask bank accountId");
        // Verify setMarkUp was called with the configured markup percentage
        verify(store).setMarkUp(MARKUP);
    }

    @Test
    public void findStores_mcpAdminAudience_passes() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);
        when(storeService.findStores(any(), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> sut.findStores(null, null, null));
    }

    @Test
    public void findStores_mcpCustomerAudience_nullBank_noNullPointer() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "Test", "test", "1 St", "img",
                "0812345678", new ArrayList<>(), new ArrayList<>(), OWNER_ID, null);
        when(storeService.findStores(isNull(), isNull(), isNull())).thenReturn(List.of(store));

        assertDoesNotThrow(() -> sut.findStores(null, null, null));
    }

    @Test
    public void findStores_nonMcp_bankStillMasked_unconditional() {
        // findStores masking is unconditional in controller (ADR-026 F1 comment).
        // Even without MCP flag the controller's forEach loop applies masking.
        StoreProfile store = storeWithBank(REAL_ACCOUNT);
        when(storeService.findStores(isNull(), isNull(), isNull())).thenReturn(List.of(store));

        List<StoreProfile> result = sut.findStores(null, null, null);
        assertEquals(MAIN_PAY_ACCOUNT, result.get(0).getBank().getAccountId(),
                "findStores masking is unconditional in the controller");
    }

    // ========== Helpers ==================================================================

    private StoreProfile storeWithBank(String accountId) {
        Bank bank = new Bank();
        bank.setAccountId(accountId);
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "Test Store", "test-store", "1 Test St",
                "https://img.test/store.png", "0812345678",
                new ArrayList<>(), new ArrayList<>(), OWNER_ID, bank);
        store.setId(STORE_ID);
        return store;
    }
}
