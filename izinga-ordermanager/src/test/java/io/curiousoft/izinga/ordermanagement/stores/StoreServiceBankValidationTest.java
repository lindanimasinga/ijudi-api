package io.curiousoft.izinga.ordermanagement.stores;

import com.google.firebase.auth.FirebaseAuth;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StoreService.validateBankForCreate() covering EWALLET-specific
 * rules and regression guards for traditional account types.
 *
 * <p>Mirror of the EWALLET section in UserProfileValidationTest.kt (Kotlin) — the two
 * validators must stay structurally identical per the T-08 codebase convention.
 */
@ExtendWith(MockitoExtension.class)
class StoreServiceBankValidationTest {

    private static final String MAIN_PAY_ACCOUNT = "123456";

    private StoreService storeService;

    @Mock private StoreRepository storeRepository;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private ReferralCodeService referralCodeService;
    @Mock private StoreAgreementAuditRepository storeAgreementAuditRepository;
    @Mock private StoreTierChangeAuditRepository storeTierChangeAuditRepository;
    @Mock private FirebaseAuth firebaseAuth;

    @BeforeEach
    void setUp() {
        storeService = new StoreService(
                storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, 0.1,
                applicationEventPublisher, referralCodeService,
                storeAgreementAuditRepository, storeTierChangeAuditRepository, firebaseAuth);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private static UserProfile ownerWithBank(String id, Bank bank) {
        UserProfile u = new UserProfile(id, UserProfile.SignUpReason.BUY,
                "address", "https://img.url", "0812815707", ProfileRoles.CUSTOMER);
        u.setId(id);
        u.setBank(bank);
        u.setIcaAccepted(true);
        return u;
    }

    private static StoreProfile storeWith(String ownerId, Bank bank) {
        ArrayList<BusinessHours> bh = new ArrayList<>();
        bh.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        StoreProfile s = new StoreProfile(StoreType.FOOD, "Test Store", "test-bv-" + ownerId,
                "1 Test St", "https://img.url", "0812815707",
                Collections.singletonList("food"), bh, ownerId, null);
        s.setIcaAccepted(true);
        s.setBank(bank);
        return s;
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // EWALLET — happy path
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * An EWALLET bank with only phone + type populated must pass validateBankForCreate.
     * The user-level bank is EWALLET (phone only), the store has no bank (null) so the
     * STORE-BANK-01 fallback copies the user's EWALLET bank to the store before validation.
     */
    @Test
    @DisplayName("EWALLET bank with only phone+type set passes validation")
    void create_ewalletBank_phoneAndTypeOnly_passesValidation() throws Exception {
        Bank ewalletBank = new Bank();
        ewalletBank.setPhone("+27812815707");
        ewalletBank.setType(BankAccType.EWALLET);
        // accountId, name, branchCode intentionally null

        // Store has no bank → STORE-BANK-01 fallback will copy the user's EWALLET bank
        UserProfile owner = ownerWithBank("owner-ewallet-01", ewalletBank);
        StoreProfile store = storeWith("owner-ewallet-01", null); // null bank → fallback triggers

        when(userProfileRepo.findById("owner-ewallet-01")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());
        when(storeRepository.save(store)).thenReturn(store);

        StoreProfile result = storeService.create(store);

        assertNotNull(result);
        // bank was inherited from user's EWALLET bank
        assertEquals(BankAccType.EWALLET, result.getBank().getType());
        assertEquals("+27812815707", result.getBank().getPhone());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // EWALLET — phone is required
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EWALLET bank missing phone fails with 'Bank phone is required'")
    void create_ewalletBank_missingPhone_throws400() throws Exception {
        Bank ewalletBank = new Bank();
        ewalletBank.setType(BankAccType.EWALLET);
        // phone intentionally null

        UserProfile owner = ownerWithBank("owner-ewallet-02", ewalletBank);
        StoreProfile store = storeWith("owner-ewallet-02", null);

        when(userProfileRepo.findById("owner-ewallet-02")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(store));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Bank phone is required", ex.getReason());
    }

    @Test
    @DisplayName("EWALLET bank with blank phone fails with 'Bank phone is required'")
    void create_ewalletBank_blankPhone_throws400() throws Exception {
        Bank ewalletBank = new Bank();
        ewalletBank.setPhone("   ");
        ewalletBank.setType(BankAccType.EWALLET);

        UserProfile owner = ownerWithBank("owner-ewallet-03", ewalletBank);
        StoreProfile store = storeWith("owner-ewallet-03", null);

        when(userProfileRepo.findById("owner-ewallet-03")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(store));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Bank phone is required", ex.getReason());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Regression guards — CHEQUE must still require accountId/name/branchCode
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CHEQUE bank missing accountId still fails with 'Bank account ID is required'")
    void create_chequeBank_missingAccountId_throws400() throws Exception {
        Bank chequeBank = new Bank();
        chequeBank.setName("FNB");
        chequeBank.setBranchCode("250655");
        chequeBank.setPhone("+27812815707");
        chequeBank.setType(BankAccType.CHEQUE);
        // accountId intentionally null

        UserProfile owner = ownerWithBank("owner-cheque-01", chequeBank);
        StoreProfile store = storeWith("owner-cheque-01", null);

        when(userProfileRepo.findById("owner-cheque-01")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(store));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Bank account ID is required", ex.getReason());
    }

    @Test
    @DisplayName("CHEQUE bank missing name still fails with 'Bank name is required'")
    void create_chequeBank_missingName_throws400() throws Exception {
        Bank chequeBank = new Bank();
        chequeBank.setAccountId("12345678");
        chequeBank.setBranchCode("250655");
        chequeBank.setPhone("+27812815707");
        chequeBank.setType(BankAccType.CHEQUE);
        // name intentionally null

        UserProfile owner = ownerWithBank("owner-cheque-02", chequeBank);
        StoreProfile store = storeWith("owner-cheque-02", null);

        when(userProfileRepo.findById("owner-cheque-02")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(store));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Bank name is required", ex.getReason());
    }

    @Test
    @DisplayName("CHEQUE bank missing branchCode still fails with 'Bank branch code is required'")
    void create_chequeBank_missingBranchCode_throws400() throws Exception {
        Bank chequeBank = new Bank();
        chequeBank.setAccountId("12345678");
        chequeBank.setName("FNB");
        chequeBank.setPhone("+27812815707");
        chequeBank.setType(BankAccType.CHEQUE);
        // branchCode intentionally null

        UserProfile owner = ownerWithBank("owner-cheque-03", chequeBank);
        StoreProfile store = storeWith("owner-cheque-03", null);

        when(userProfileRepo.findById("owner-cheque-03")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(store));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Bank branch code is required", ex.getReason());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Legacy type rejection — wallet and string types remain invalid
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Legacy 'wallet' BankAccType is still rejected")
    void create_walletLegacyType_throws400() throws Exception {
        Bank legacyBank = new Bank();
        legacyBank.setPhone("+27812815707");
        legacyBank.setType(BankAccType.wallet);

        UserProfile owner = ownerWithBank("owner-legacy-01", legacyBank);
        StoreProfile store = storeWith("owner-legacy-01", null);

        when(userProfileRepo.findById("owner-legacy-01")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(store));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("wallet"));
    }
}
