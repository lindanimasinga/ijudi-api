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

import java.time.DayOfWeek;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * STORE-BANK-01 — Per-store payout bank selection during store creation.
 *
 * <p>Prior to this fix, StoreService.create() unconditionally overwrote the frontend-submitted
 * bank with the owner's personal user-level bank, making per-store payout routing impossible.
 *
 * <p>After the fix the resolution order is:
 * <ol>
 *   <li>If {@code profile.getBank()} is non-null AND has a non-blank {@code accountId} →
 *       use the store-submitted bank.</li>
 *   <li>Otherwise (null bank or empty/blank accountId) → fall back to the owner's
 *       user-level bank, preserving pre-fix behaviour.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class StoreServiceBankSelectionTest {

    private static final String MAIN_PAY_ACCOUNT = "123456";

    private StoreService storeService;

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserProfileRepo userProfileRepo;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private ReferralCodeService referralCodeService;
    @Mock
    private StoreAgreementAuditRepository storeAgreementAuditRepository;
    @Mock
    private StoreTierChangeAuditRepository storeTierChangeAuditRepository;
    @Mock
    private FirebaseAuth firebaseAuth;

    @BeforeEach
    void setUp() {
        storeService = new StoreService(
                storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, 0.1,
                applicationEventPublisher, referralCodeService,
                storeAgreementAuditRepository, storeTierChangeAuditRepository, firebaseAuth);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /** Builds a valid Bank with the given accountId. */
    private static Bank bank(String accountId, String bankName, String branchCode) {
        Bank b = new Bank();
        b.setAccountId(accountId);
        b.setName(bankName);
        b.setPhone("+27812815707");
        b.setType(BankAccType.CHEQUE);
        b.setBranchCode(branchCode);
        return b;
    }

    /** Builds a minimal UserProfile with a fully valid bank. */
    private static UserProfile userWithBank(String id, Bank userBank) {
        UserProfile u = new UserProfile(id, UserProfile.SignUpReason.BUY,
                "address", "https://img.url", "0812815707", ProfileRoles.CUSTOMER);
        u.setId(id);
        u.setBank(userBank);
        return u;
    }

    /** Builds a minimal StoreProfile and sets ICA accepted. */
    private static StoreProfile storeProfile(String ownerId) {
        ArrayList<BusinessHours> bh = new ArrayList<>();
        bh.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        StoreProfile s = new StoreProfile(StoreType.FOOD, "Test Store", "test-store-" + ownerId,
                "1 Test St", "https://img.url", "0812815707",
                Collections.singletonList("food"), bh, ownerId, null);
        s.setIcaAccepted(true);
        return s;
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // STORE-BANK-01 happy path — store-submitted bank with populated accountId is used
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("STORE-BANK-01: store-level bank with accountId is persisted — user bank is NOT used")
    void create_withPopulatedStoreLevelBank_usesStoreBankNotUserBank() throws Exception {
        // given — owner's personal bank
        Bank userBank = bank("USER-ACC-ID", "FNB", "250655");
        UserProfile owner = userWithBank("owner-sb-01", userBank);

        // given — store-specific bank with a DIFFERENT accountId
        Bank storeBank = bank("STORE-ACC-ID", "Standard Bank", "051001");

        StoreProfile store = storeProfile("owner-sb-01");
        store.setBank(storeBank);

        when(userProfileRepo.findById("owner-sb-01")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());
        when(storeRepository.save(store)).thenReturn(store);

        // when
        StoreProfile result = storeService.create(store);

        // then — store-level bank is used, NOT the user's bank
        assertNotNull(result.getBank(), "bank must not be null after create");
        assertEquals("STORE-ACC-ID", result.getBank().getAccountId(),
                "store-level accountId must be used when the frontend submits one");
        assertEquals("Standard Bank", result.getBank().getName(),
                "store-level bank name must be preserved");
        assertNotEquals("USER-ACC-ID", result.getBank().getAccountId(),
                "user's personal bank must NOT overwrite the store-submitted bank");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // STORE-BANK-01 fallback path — null bank falls back to user bank
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("STORE-BANK-01 fallback: null store bank falls back to user bank")
    void create_withNullBank_fallsBackToUserBank() throws Exception {
        // given — owner's personal bank
        Bank userBank = bank("USER-FALLBACK-ACC", "FNB", "250655");
        UserProfile owner = userWithBank("owner-sb-02", userBank);

        // given — store has no bank submitted (null)
        StoreProfile store = storeProfile("owner-sb-02");
        store.setBank(null);

        when(userProfileRepo.findById("owner-sb-02")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());
        when(storeRepository.save(store)).thenReturn(store);

        // when
        StoreProfile result = storeService.create(store);

        // then — falls back to the user's personal bank
        assertNotNull(result.getBank(), "bank must not be null after create with fallback");
        assertEquals("USER-FALLBACK-ACC", result.getBank().getAccountId(),
                "user-level bank must be used when the store submits no bank");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // STORE-BANK-01 fallback path — empty Bank() (no accountId) falls back to user bank
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("STORE-BANK-01 fallback: empty Bank (blank accountId) falls back to user bank")
    void create_withEmptyBankNoAccountId_fallsBackToUserBank() throws Exception {
        // given — owner's personal bank
        Bank userBank = bank("USER-EMPTY-FALLBACK", "Capitec", "470010");
        UserProfile owner = userWithBank("owner-sb-03", userBank);

        // given — store bank is a new Bank() with no accountId (simulates Jackson deserializing {})
        StoreProfile store = storeProfile("owner-sb-03");
        store.setBank(new Bank()); // non-null but blank accountId

        when(userProfileRepo.findById("owner-sb-03")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());
        when(storeRepository.save(store)).thenReturn(store);

        // when
        StoreProfile result = storeService.create(store);

        // then — falls back to the user's personal bank
        assertNotNull(result.getBank(), "bank must not be null");
        assertEquals("USER-EMPTY-FALLBACK", result.getBank().getAccountId(),
                "user-level bank must be used when store bank has no accountId");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // STORE-BANK-01 EWALLET fix — store submits its own EWALLET bank (phone set, accountId blank)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Bug: prior condition `!StringUtils.hasText(accountId)` was true for any EWALLET bank
     * (accountId is intentionally absent), so the fallback silently overwrote the store's own
     * EWALLET bank with the user's personal bank.
     *
     * Fixed condition: EWALLET bank with non-blank phone is treated as "meaningfully submitted" —
     * the fallback must NOT fire.
     */
    @Test
    @DisplayName("STORE-BANK-01 EWALLET fix: store's own EWALLET bank (phone set, accountId blank) is kept — user bank NOT used")
    void create_withOwnEwalletBank_phoneSet_noAccountId_usesStoreBankNotUserBank() throws Exception {
        // given — owner's personal bank is a CHEQUE account with a distinct accountId
        Bank userBank = bank("USER-CHEQUE-ACC", "FNB", "250655");
        UserProfile owner = userWithBank("owner-ewallet-sel-01", userBank);

        // given — store submits its own EWALLET bank: phone is the identifier; accountId is blank
        Bank storeEwalletBank = new Bank();
        storeEwalletBank.setType(BankAccType.EWALLET);
        storeEwalletBank.setPhone("+27831234567");
        // accountId intentionally NOT set — EWALLET accounts don't use one

        StoreProfile store = storeProfile("owner-ewallet-sel-01");
        store.setBank(storeEwalletBank);

        when(userProfileRepo.findById("owner-ewallet-sel-01")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());
        when(storeRepository.save(store)).thenReturn(store);

        // when
        StoreProfile result = storeService.create(store);

        // then — the store's own EWALLET bank is used, NOT the user's CHEQUE bank
        assertNotNull(result.getBank(), "bank must not be null after create");
        assertEquals(BankAccType.EWALLET, result.getBank().getType(),
                "bank type must be EWALLET (the store's own bank), not the user's CHEQUE bank");
        assertEquals("+27831234567", result.getBank().getPhone(),
                "EWALLET phone must be the store-submitted one, not the user's");
        assertNull(result.getBank().getAccountId(),
                "accountId must remain null — EWALLET banks do not have one");
        // The user's personal bank must never have been used
        assertNotEquals("USER-CHEQUE-ACC", result.getBank().getAccountId());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // STORE-BANK-01 EWALLET fallback — EWALLET bank with no phone is NOT meaningful → fallback fires
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * An EWALLET bank with a blank/null phone has no identifier at all and cannot be used
     * for payouts. The updated condition correctly leaves it as non-meaningful, so the fallback
     * still copies the user's personal bank — same as a null or empty bank.
     */
    @Test
    @DisplayName("STORE-BANK-01 EWALLET fallback: EWALLET bank with blank phone is not meaningful — falls back to user bank")
    void create_withEwalletBankMissingPhone_fallsBackToUserBank() throws Exception {
        // given — owner's personal bank is a fully valid CHEQUE account
        Bank userBank = bank("USER-CHEQUE-FALLBACK", "Standard Bank", "051001");
        UserProfile owner = userWithBank("owner-ewallet-sel-02", userBank);

        // given — store submits an EWALLET bank stub with NO phone (unusable for payouts)
        Bank incompleteEwallet = new Bank();
        incompleteEwallet.setType(BankAccType.EWALLET);
        // phone intentionally NOT set — this EWALLET bank has no identifier

        StoreProfile store = storeProfile("owner-ewallet-sel-02");
        store.setBank(incompleteEwallet);

        when(userProfileRepo.findById("owner-ewallet-sel-02")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store.getId(), store.getShortName()))
                .thenReturn(Optional.empty());
        when(storeRepository.save(store)).thenReturn(store);

        // when
        StoreProfile result = storeService.create(store);

        // then — falls back to the user's personal CHEQUE bank (the EWALLET stub has no identifier)
        assertNotNull(result.getBank(), "bank must not be null after fallback");
        assertEquals("USER-CHEQUE-FALLBACK", result.getBank().getAccountId(),
                "user-level bank must be used when the store's EWALLET bank has no phone");
        assertEquals(BankAccType.CHEQUE, result.getBank().getType(),
                "bank type must be the user's CHEQUE, not the meaningless EWALLET stub");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // STORE-BANK-01 multi-store: second store can route to a different account
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("STORE-BANK-01 multi-store: each store can have a distinct payout account")
    void create_multipleStores_eachUsesOwnBank() throws Exception {
        // given — single owner with a personal bank
        Bank userBank = bank("OWNER-PERSONAL-ACC", "FNB", "250655");
        UserProfile owner = userWithBank("owner-multistore", userBank);

        // given — two stores, each with distinct bank accounts
        Bank store1Bank = bank("STORE-1-ACC", "Standard Bank", "051001");
        StoreProfile store1 = storeProfile("owner-multistore");
        store1.setBank(store1Bank);

        Bank store2Bank = bank("STORE-2-ACC", "Absa", "632005");
        StoreProfile store2 = storeProfile("owner-multistore");
        store2 = new StoreProfile(StoreType.FOOD, "Test Store 2", "test-store-2-owner-multistore",
                "2 Test St", "https://img.url", "0812815707",
                Collections.singletonList("food"),
                Collections.singletonList(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date())),
                "owner-multistore", null);
        store2.setIcaAccepted(true);
        store2.setBank(store2Bank);

        when(userProfileRepo.findById("owner-multistore")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(store1.getId(), store1.getShortName()))
                .thenReturn(Optional.empty());
        when(storeRepository.findOneByIdOrShortName(store2.getId(), store2.getShortName()))
                .thenReturn(Optional.empty());
        when(storeRepository.save(store1)).thenReturn(store1);
        when(storeRepository.save(store2)).thenReturn(store2);

        // when
        StoreProfile result1 = storeService.create(store1);
        StoreProfile result2 = storeService.create(store2);

        // then — each store retains its own bank
        assertEquals("STORE-1-ACC", result1.getBank().getAccountId(),
                "Store 1 must use its own bank account");
        assertEquals("STORE-2-ACC", result2.getBank().getAccountId(),
                "Store 2 must use its own bank account — not store 1's nor the owner's");
        assertNotEquals(result1.getBank().getAccountId(), result2.getBank().getAccountId(),
                "The two stores must have distinct bank accounts");
    }
}
