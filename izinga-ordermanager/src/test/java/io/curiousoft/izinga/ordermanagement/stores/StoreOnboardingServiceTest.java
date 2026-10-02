package io.curiousoft.izinga.ordermanagement.stores;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ONB-02: Unit tests for T-06 (acceptIca), T-07 (C-04 gate in create), T-08 (bank validation),
 * and T-09 (updateSubscriptionTier) in {@link StoreService}.
 */
@ExtendWith(MockitoExtension.class)
public class StoreOnboardingServiceTest {

    private static final String STORE_ID = "store-001";
    private static final String USER_ID = "user-001";
    private static final String ICA_VERSION = "merchant-v2";
    private static final String MAIN_PAY_ACCOUNT = "main-pay";

    private StoreService storeService;

    @Mock private StoreRepository storeRepository;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ReferralCodeService referralCodeService;
    @Mock private StoreAgreementAuditRepository storeAgreementAuditRepository;
    @Mock private StoreTierChangeAuditRepository storeTierChangeAuditRepository;

    @BeforeEach
    public void setUp() {
        storeService = new StoreService(
                storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, 0.1,
                eventPublisher, referralCodeService,
                storeAgreementAuditRepository, storeTierChangeAuditRepository);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // T-07: C-04 ICA gate in create()
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void create_storeAdminWithoutIcaAccepted_throws403() {
        UserProfile user = userWithRole(ProfileRoles.STORE_ADMIN);
        StoreProfile profile = storeProfile("owner-001");
        // icaAccepted is null (not set)

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("MERCHANT_ICA_NOT_ACCEPTED", ex.getReason());
        verify(storeRepository, never()).save(any());
    }

    @Test
    public void create_storeAdminWithIcaAcceptedFalse_throws403() {
        UserProfile user = userWithRole(ProfileRoles.STORE_ADMIN);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(false); // explicitly false

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    public void create_storeAdminWithIcaAcceptedTrue_succeeds() throws Exception {
        UserProfile user = userWithRole(ProfileRoles.STORE_ADMIN);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(true);
        profile.setIcaVersion(ICA_VERSION);

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.create(profile);
        assertNotNull(result);
        verify(storeRepository).save(any());
    }

    /**
     * DEFECT-ONB02-01: verify the bug is gone — a CUSTOMER-role first-time creator
     * must NOT be able to bypass the ICA gate.
     * Before the fix this test would have PASSED (store was created), confirming the defect.
     */
    @Test
    public void create_customerRoleFirstTimeCreator_withoutIcaAccepted_throws403() {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        StoreProfile profile = storeProfile("owner-001");
        // icaAccepted not set (null) — must be rejected

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("MERCHANT_ICA_NOT_ACCEPTED", ex.getReason());
        verify(storeRepository, never()).save(any());
        verify(userProfileRepo, never()).save(any());
    }

    /** DEFECT-ONB02-01: explicit false also blocked for first-time CUSTOMER creator. */
    @Test
    public void create_customerRoleFirstTimeCreator_withIcaAcceptedFalse_throws403() {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(false);

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("MERCHANT_ICA_NOT_ACCEPTED", ex.getReason());
        verify(storeRepository, never()).save(any());
    }

    /** DEFECT-ONB02-01: happy path — CUSTOMER with ICA accepted creates store and is upgraded to STORE_ADMIN. */
    @Test
    public void create_customerRoleFirstTimeCreator_withIcaAcceptedTrue_succeeds() throws Exception {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(true);
        profile.setIcaVersion(ICA_VERSION);

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.create(profile);
        assertNotNull(result);
        verify(storeRepository).save(any());
        // Role must be upgraded after store creation
        assertEquals(ProfileRoles.STORE_ADMIN, user.getRole());
        verify(userProfileRepo).save(user);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // T-08: Bank validation in create()
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void create_nullBank_throws400() {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        user.setBank(null);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(true); // ICA accepted so gate passes; bank validation fires

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void create_missingAccountId_throws400() {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        Bank bank = validBank();
        bank.setAccountId(null);
        user.setBank(bank);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(true); // ICA accepted so gate passes; bank validation fires

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void create_missingBranchCode_throws400() {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        Bank bank = validBank();
        bank.setBranchCode(null);
        user.setBank(bank);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(true); // ICA accepted so gate passes; bank validation fires

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void create_legacyBankTypeWallet_throws400() {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        Bank bank = validBank();
        bank.setType(BankAccType.wallet);
        user.setBank(bank);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(true); // ICA accepted so gate passes; bank validation fires

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void create_legacyBankTypeString_throws400() {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        Bank bank = validBank();
        bank.setType(BankAccType.string);
        user.setBank(bank);
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(true); // ICA accepted so gate passes; bank validation fires

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.create(profile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void create_validBank_succeeds() throws Exception {
        UserProfile user = userWithRole(ProfileRoles.CUSTOMER);
        user.setBank(validBank());
        StoreProfile profile = storeProfile("owner-001");
        profile.setIcaAccepted(true); // ICA accepted so gate passes

        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.create(profile);
        assertNotNull(result);
        verify(storeRepository).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // T-06: acceptIca()
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void acceptIca_happyPath_updatesStoreAndWritesAudit() throws Exception {
        StoreProfile store = persistedStore();
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeAgreementAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.acceptIca(STORE_ID, USER_ID, true, ICA_VERSION,
                "127.0.0.1", "Mozilla/5.0", STORE_ID, false);

        assertTrue(Boolean.TRUE.equals(result.getIcaAccepted()));
        assertEquals(ICA_VERSION, result.getIcaVersion());
        assertNotNull(result.getIcaAcceptedDate());

        // Verify audit written with all SEC-ONB02-01-B fields
        ArgumentCaptor<StoreAgreementAudit> auditCaptor = ArgumentCaptor.forClass(StoreAgreementAudit.class);
        verify(storeAgreementAuditRepository).save(auditCaptor.capture());
        StoreAgreementAudit audit = auditCaptor.getValue();
        assertEquals(STORE_ID, audit.getStoreId());
        assertEquals(USER_ID, audit.getAcceptedByUserId());
        assertEquals(ICA_VERSION, audit.getIcaVersion());
        assertEquals("127.0.0.1", audit.getIpAddress());
        assertEquals("Mozilla/5.0", audit.getUserAgent());
        assertEquals("WHATSAPP_OTP", audit.getVerificationChannel());
        assertNotNull(audit.getAcceptedAt());
    }

    @Test
    public void acceptIca_alreadyAcceptedSameVersion_idempotentNoAudit() throws Exception {
        StoreProfile store = persistedStore();
        store.setIcaAccepted(true);
        store.setIcaVersion(ICA_VERSION);
        store.setIcaAcceptedDate(new Date());
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        StoreProfile result = storeService.acceptIca(STORE_ID, USER_ID, true, ICA_VERSION,
                null, null, STORE_ID, false);

        assertNotNull(result);
        // No audit record written — idempotent
        verify(storeAgreementAuditRepository, never()).save(any());
        verify(storeRepository, never()).save(any());
    }

    @Test
    public void acceptIca_icaAcceptedFalse_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.acceptIca(STORE_ID, USER_ID, false, ICA_VERSION,
                        null, null, STORE_ID, false));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(storeRepository);
    }

    @Test
    public void acceptIca_blankIcaVersion_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.acceptIca(STORE_ID, USER_ID, true, "",
                        null, null, STORE_ID, false));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(storeRepository);
    }

    @Test
    public void acceptIca_idorCheck_nonAdminWrongJwtStoreId_throws403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.acceptIca(STORE_ID, USER_ID, true, ICA_VERSION,
                        null, null, "different-store-id", false));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(storeRepository);
    }

    @Test
    public void acceptIca_idorCheck_nonAdminNullJwtStoreId_throws403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.acceptIca(STORE_ID, USER_ID, true, ICA_VERSION,
                        null, null, null, false));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    public void acceptIca_adminBypassesIdorCheck() throws Exception {
        StoreProfile store = persistedStore();
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeAgreementAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // isAdmin=true, jwtStoreId does not match
        StoreProfile result = storeService.acceptIca(STORE_ID, USER_ID, true, ICA_VERSION,
                null, null, "admin-has-no-store-id", true);

        assertNotNull(result);
        verify(storeAgreementAuditRepository).save(any());
    }

    /**
     * AC-06 — ADR-022 Decision 1: ICA fields must be written to StoreProfile ONLY.
     * UserProfile must not be mutated during acceptIca().
     */
    @Test
    public void acceptIca_doesNotTouchUserProfile() throws Exception {
        StoreProfile store = persistedStore();
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeAgreementAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        storeService.acceptIca(STORE_ID, USER_ID, true, ICA_VERSION,
                "127.0.0.1", "Mozilla/5.0", STORE_ID, false);

        // UserProfile repository must not be read or written during ICA acceptance
        verifyNoInteractions(userProfileRepo);
    }

    @Test
    public void acceptIca_auditFailure_rollsBackStoreProfile() throws Exception {
        StoreProfile store = persistedStore();
        Boolean originalIcaAccepted = store.getIcaAccepted();
        String originalIcaVersion = store.getIcaVersion();

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeAgreementAuditRepository.save(any())).thenThrow(new RuntimeException("MongoDB down"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> storeService.acceptIca(STORE_ID, USER_ID, true, ICA_VERSION,
                        null, null, STORE_ID, false));
        assertTrue(ex.getMessage().contains("audit write failed"));

        // StoreProfile should have been rolled back (saved twice: once forward, once back)
        verify(storeRepository, times(2)).save(any());
        assertEquals(originalIcaAccepted, store.getIcaAccepted(),
                "ICA accepted must be rolled back");
        assertEquals(originalIcaVersion, store.getIcaVersion(),
                "ICA version must be rolled back");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // T-09: updateSubscriptionTier()
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void updateSubscriptionTier_happyPath_updatesAndWritesAudit() throws Exception {
        StoreProfile store = persistedStore();
        store.setIcaAccepted(true);
        store.setSubscriptionTier(SubscriptionTier.FREE);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeTierChangeAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.updateSubscriptionTier(
                STORE_ID, USER_ID, SubscriptionTier.PREMIUM_1, STORE_ID, false);

        assertEquals(SubscriptionTier.PREMIUM_1, result.getSubscriptionTier());
        assertNotNull(result.getSubscriptionTierSince());

        ArgumentCaptor<StoreTierChangeAudit> auditCaptor = ArgumentCaptor.forClass(StoreTierChangeAudit.class);
        verify(storeTierChangeAuditRepository).save(auditCaptor.capture());
        StoreTierChangeAudit audit = auditCaptor.getValue();
        assertEquals(STORE_ID, audit.getStoreId());
        assertEquals(USER_ID, audit.getChangedByUserId());
        assertEquals(SubscriptionTier.FREE, audit.getFromTier());
        assertEquals(SubscriptionTier.PREMIUM_1, audit.getToTier());
        assertNotNull(audit.getChangedAt());
    }

    @Test
    public void updateSubscriptionTier_nullTier_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.updateSubscriptionTier(STORE_ID, USER_ID, null, STORE_ID, false));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(storeRepository);
    }

    @Test
    public void updateSubscriptionTier_idorCheck_nonAdminWrongJwtStoreId_throws403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.updateSubscriptionTier(STORE_ID, USER_ID, SubscriptionTier.PREMIUM_1,
                        "wrong-store-id", false));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(storeRepository);
    }

    @Test
    public void updateSubscriptionTier_icaNotAccepted_throws422() {
        StoreProfile store = persistedStore();
        store.setIcaAccepted(null); // not accepted
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.updateSubscriptionTier(STORE_ID, USER_ID, SubscriptionTier.PREMIUM_1,
                        STORE_ID, false));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    public void updateSubscriptionTier_adminBypassesIdorCheck() throws Exception {
        StoreProfile store = persistedStore();
        store.setIcaAccepted(true);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeTierChangeAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // isAdmin=true, jwtStoreId does not match
        StoreProfile result = storeService.updateSubscriptionTier(
                STORE_ID, USER_ID, SubscriptionTier.PREMIUM_2, "admin-has-no-store-id", true);

        assertNotNull(result);
        verify(storeTierChangeAuditRepository).save(any());
    }

    @Test
    public void updateSubscriptionTier_storeNotFound_throws404() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.updateSubscriptionTier(STORE_ID, USER_ID, SubscriptionTier.PREMIUM_1,
                        STORE_ID, false));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private UserProfile userWithRole(ProfileRoles role) {
        UserProfile user = new UserProfile("name", UserProfile.SignUpReason.SELL,
                "address", "https://img.test/u.png", "0811111111", role);
        user.setId("owner-001");
        user.setBank(validBank());
        return user;
    }

    private Bank validBank() {
        Bank bank = new Bank();
        bank.setAccountId("acc-123");
        bank.setName("fnb");
        bank.setPhone("0800111222");
        bank.setBranchCode("051001");
        bank.setType(BankAccType.CHEQUE);
        return bank;
    }

    private StoreProfile storeProfile(String ownerId) {
        ArrayList<BusinessHours> hours = new ArrayList<>();
        hours.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        StoreProfile p = new StoreProfile(
                StoreType.FOOD, "Test Store", "test-store-" + UUID.randomUUID().toString().substring(0, 8),
                "1 Store St", "https://img.test/s.png", "0811111111",
                Collections.singletonList("food"), hours, ownerId, new Bank());
        return p;
    }

    private StoreProfile persistedStore() {
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "Persisted Store", "persisted-shortname",
                "1 Store St", "https://img.test/s.png", "0811111111",
                Collections.singletonList("food"),
                Collections.singletonList(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date())),
                "owner-001", validBank());
        store.setId(STORE_ID);
        return store;
    }
}
