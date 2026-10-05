package io.curiousoft.izinga.ordermanagement.stores;

import com.google.firebase.auth.FirebaseAuth;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionActivatedEvent;
import java.time.DayOfWeek;
import io.curiousoft.izinga.ordermanagement.stores.StoreAgreementAuditRepository;
import io.curiousoft.izinga.ordermanagement.stores.StoreTierChangeAuditRepository;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TIER-BILLING-01 / REQ-05: Unit tests for the [StoreService.onMerchantSubscriptionActivated]
 * event listener.
 *
 * Covers:
 * - AC-14: Event triggers updateSubscriptionTier for the correct storeId and tier
 * - Listener does not re-throw exceptions from updateSubscriptionTier (fire-and-log pattern)
 * - isAdmin=true bypasses IDOR check (system event path)
 */
@ExtendWith(MockitoExtension.class)
public class StoreServiceSubscriptionEventListenerTest {

    private static final String STORE_ID = "store-event-001";
    private static final String OWNER_ID = "owner-event-001";

    private StoreService storeService;

    @Mock private StoreRepository storeRepository;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ReferralCodeService referralCodeService;
    @Mock private StoreAgreementAuditRepository storeAgreementAuditRepository;
    @Mock private StoreTierChangeAuditRepository storeTierChangeAuditRepository;
    @Mock private FirebaseAuth firebaseAuth;

    @BeforeEach
    public void setUp() {
        storeService = new StoreService(
                storeRepository, userProfileRepo, "main-pay", 0.1,
                eventPublisher, referralCodeService,
                storeAgreementAuditRepository, storeTierChangeAuditRepository, firebaseAuth);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // AC-14: PREMIUM_1 event → StoreProfile.subscriptionTier = PREMIUM_1
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void onMerchantSubscriptionActivated_premium1_updatesTierAndTimestamp() throws Exception {
        StoreProfile store = buildStore(STORE_ID, OWNER_ID, true);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storeTierChangeAuditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MerchantSubscriptionActivatedEvent event = new MerchantSubscriptionActivatedEvent(
                STORE_ID, SubscriptionTier.PREMIUM_1, OWNER_ID);

        // Must not throw
        storeService.onMerchantSubscriptionActivated(event);

        // StoreProfile should have been saved with PREMIUM_1 tier
        ArgumentCaptor<StoreProfile> captor = ArgumentCaptor.forClass(StoreProfile.class);
        verify(storeRepository).save(captor.capture());
        assertEquals(SubscriptionTier.PREMIUM_1, captor.getValue().getSubscriptionTier());
        assertNotNull(captor.getValue().getSubscriptionTierSince());
    }

    @Test
    public void onMerchantSubscriptionActivated_premium2_updatesTierToPremium2() throws Exception {
        StoreProfile store = buildStore(STORE_ID, OWNER_ID, true);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storeTierChangeAuditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MerchantSubscriptionActivatedEvent event = new MerchantSubscriptionActivatedEvent(
                STORE_ID, SubscriptionTier.PREMIUM_2, OWNER_ID);

        storeService.onMerchantSubscriptionActivated(event);

        ArgumentCaptor<StoreProfile> captor = ArgumentCaptor.forClass(StoreProfile.class);
        verify(storeRepository).save(captor.capture());
        assertEquals(SubscriptionTier.PREMIUM_2, captor.getValue().getSubscriptionTier());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // isAdmin=true bypasses IDOR check — no jwtStoreId claim needed
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void onMerchantSubscriptionActivated_bypassesIcaCheck_whenIcaAccepted() {
        // The isAdmin=true path bypasses the IDOR check but still requires ICA.
        // ICA is accepted — should succeed.
        StoreProfile store = buildStore(STORE_ID, OWNER_ID, true);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storeTierChangeAuditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MerchantSubscriptionActivatedEvent event = new MerchantSubscriptionActivatedEvent(
                STORE_ID, SubscriptionTier.PREMIUM_1, OWNER_ID);
        // Must not throw
        assertDoesNotThrow(() -> storeService.onMerchantSubscriptionActivated(event));
        verify(storeRepository, times(1)).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Error handling: exception from updateSubscriptionTier must not propagate
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void onMerchantSubscriptionActivated_logsErrorButDoesNotThrow_whenStoreNotFound() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        MerchantSubscriptionActivatedEvent event = new MerchantSubscriptionActivatedEvent(
                STORE_ID, SubscriptionTier.PREMIUM_1, OWNER_ID);

        // Must not propagate the exception — fire-and-log pattern
        assertDoesNotThrow(() -> storeService.onMerchantSubscriptionActivated(event));
        verify(storeRepository, never()).save(any());
    }

    @Test
    public void onMerchantSubscriptionActivated_logsErrorButDoesNotThrow_whenRepoThrows() {
        StoreProfile store = buildStore(STORE_ID, OWNER_ID, true);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenThrow(new RuntimeException("MongoDB unavailable"));

        MerchantSubscriptionActivatedEvent event = new MerchantSubscriptionActivatedEvent(
                STORE_ID, SubscriptionTier.PREMIUM_1, OWNER_ID);

        // Must not propagate the exception
        assertDoesNotThrow(() -> storeService.onMerchantSubscriptionActivated(event));
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Audit record written for tier change
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void onMerchantSubscriptionActivated_writesStoreTierChangeAudit() throws Exception {
        StoreProfile store = buildStore(STORE_ID, OWNER_ID, true);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storeTierChangeAuditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MerchantSubscriptionActivatedEvent event = new MerchantSubscriptionActivatedEvent(
                STORE_ID, SubscriptionTier.PREMIUM_1, OWNER_ID);

        storeService.onMerchantSubscriptionActivated(event);

        verify(storeTierChangeAuditRepository, times(1)).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private StoreProfile buildStore(String storeId, String ownerId, boolean icaAccepted) {
        java.util.ArrayList<io.curiousoft.izinga.commons.model.BusinessHours> hours = new java.util.ArrayList<>();
        hours.add(new io.curiousoft.izinga.commons.model.BusinessHours(
            java.time.DayOfWeek.MONDAY, new java.util.Date(), new java.util.Date()));
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "Test Store", "test-store-event-" + storeId,
                "1 Test St", "https://img.test/s.png", "0811111111",
                java.util.Collections.singletonList("food"), hours, ownerId, new Bank());
        store.setId(storeId);
        store.setIcaAccepted(icaAccepted);
        return store;
    }
}
