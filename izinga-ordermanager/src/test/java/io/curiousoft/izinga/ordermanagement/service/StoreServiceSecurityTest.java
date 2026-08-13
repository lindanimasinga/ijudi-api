package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * SEC-01: Unit tests for the ownership + admin authorisation overloads added to StoreService.
 *
 * Tests cover:
 *  update(id, profile, auth) — owner can update, admin can update any, other user gets 403
 *  addStockForShop(id, stock, auth) — owner can update, admin can update any, other user gets 403
 *  Both overloads guard against a null/missing store (profile not found case).
 */
@ExtendWith(MockitoExtension.class)
class StoreServiceSecurityTest {

    private static final String MAIN_PAY_ACCOUNT = "main-account";
    private static final String OWNER_ID = "owner-user-id";
    private static final String OTHER_USER_ID = "other-user-id";
    private static final String STORE_ID = "store-id";

    private StoreService storeService;

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserProfileRepo userProfileRepo;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private ReferralCodeService referralCodeService;

    @BeforeEach
    void setUp() {
        storeService = new StoreService(
                storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, 0.0,
                applicationEventPublisher, referralCodeService);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // update(id, profile, auth)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void update_ownerCanUpdateOwnStore() throws Exception {
        StoreProfile persisted = makeStore(STORE_ID, OWNER_ID);
        StoreProfile incoming = makeStore(STORE_ID, OWNER_ID);
        Authentication auth = mockAuth(OWNER_ID, false);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.update(STORE_ID, incoming, auth);

        assertNotNull(result);
        verify(storeRepository, atLeastOnce()).save(any(StoreProfile.class));
    }

    @Test
    void update_adminCanUpdateAnyStore() throws Exception {
        StoreProfile persisted = makeStore(STORE_ID, OWNER_ID);
        StoreProfile incoming = makeStore(STORE_ID, OWNER_ID);
        // admin principal is a different user
        Authentication auth = mockAuth(OTHER_USER_ID, true);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.update(STORE_ID, incoming, auth);

        assertNotNull(result);
        verify(storeRepository, atLeastOnce()).save(any(StoreProfile.class));
    }

    @Test
    void update_differentNonAdminUserGets403() {
        StoreProfile persisted = makeStore(STORE_ID, OWNER_ID);
        Authentication auth = mockAuth(OTHER_USER_ID, false);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(persisted));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.update(STORE_ID, makeStore(STORE_ID, OWNER_ID), auth));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(storeRepository, never()).save(any());
    }

    /**
     * GAP-3a: proves the ownership check reads the PERSISTED store's ownerId, not the incoming body.
     *
     * Setup: persisted.ownerId = OWNER_ID; incoming body ownerId is deliberately different ("spoofed-id").
     * Auth principal = OWNER_ID (matches persisted, not incoming body).
     *
     * If the code accidentally used incoming.getOwnerId() it would compare OWNER_ID vs "spoofed-id"
     * and throw FORBIDDEN.  Since it uses persisted.getOwnerId() the check passes and save is called.
     */
    @Test
    void update_persistedOwnerMatchesAuth_succeedsEvenWhenIncomingBodyOwnerIdDiffers() throws Exception {
        StoreProfile persisted = makeStore(STORE_ID, OWNER_ID);
        StoreProfile incoming = makeStore(STORE_ID, "spoofed-id"); // deliberately different from persisted
        Authentication auth = mockAuth(OWNER_ID, false); // auth principal matches PERSISTED ownerId

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        StoreProfile result = storeService.update(STORE_ID, incoming, auth);

        assertNotNull(result, "Caller whose id matches persisted ownerId must be allowed even when the request body carries a different ownerId");
        verify(storeRepository, atLeastOnce()).save(any(StoreProfile.class));
    }

    /**
     * GAP-3b: proves that spoofing the incoming body's ownerId to match the auth principal does not
     * bypass the persisted-record check.
     *
     * Setup: persisted.ownerId = OWNER_ID (the real owner).
     * Attacker sends request with their own id in the body AND as the auth principal, but the
     * persisted record still has OWNER_ID.  The check must use persisted.getOwnerId() and reject.
     */
    @Test
    void update_spoofedIncomingOwnerIdMatchingAuth_doesNotBypassPersistedCheck() {
        StoreProfile persisted = makeStore(STORE_ID, OWNER_ID); // real owner in DB
        StoreProfile incoming = makeStore(STORE_ID, OTHER_USER_ID); // attacker puts their own id in body
        Authentication auth = mockAuth(OTHER_USER_ID, false); // attacker's auth token

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(persisted));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.update(STORE_ID, incoming, auth));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode(),
                "Spoofing incoming body ownerId to match auth must still be FORBIDDEN when it differs from the persisted ownerId");
        verify(storeRepository, never()).save(any());
    }

    @Test
    void update_storeNotFoundThrowsException() {
        Authentication auth = mockAuth(OWNER_ID, false);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class,
                () -> storeService.update(STORE_ID, makeStore(STORE_ID, OWNER_ID), auth));

        assertNotNull(ex.getMessage());
        verify(storeRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // addStockForShop(id, stock, auth)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void addStockForShop_ownerCanUpdateStock() throws Exception {
        StoreProfile persisted = makeStore(STORE_ID, OWNER_ID);
        Stock stock = new Stock("item", 10, 20.0, 0, Collections.emptyList());
        Authentication auth = mockAuth(OWNER_ID, false);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        storeService.addStockForShop(STORE_ID, stock, auth);

        // inner addStockForShop also calls findById — at least once for the auth check
        verify(storeRepository, atLeast(1)).findById(STORE_ID);
        verify(storeRepository).save(persisted);
    }

    @Test
    void addStockForShop_adminCanUpdateAnyStock() throws Exception {
        StoreProfile persisted = makeStore(STORE_ID, OWNER_ID);
        Stock stock = new Stock("item", 10, 20.0, 0, Collections.emptyList());
        Authentication auth = mockAuth(OTHER_USER_ID, true);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        storeService.addStockForShop(STORE_ID, stock, auth);

        verify(storeRepository, atLeast(1)).findById(STORE_ID);
        verify(storeRepository).save(persisted);
    }

    @Test
    void addStockForShop_differentNonAdminUserGets403() {
        StoreProfile persisted = makeStore(STORE_ID, OWNER_ID);
        Stock stock = new Stock("item", 10, 20.0, 0, Collections.emptyList());
        Authentication auth = mockAuth(OTHER_USER_ID, false);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(persisted));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storeService.addStockForShop(STORE_ID, stock, auth));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(storeRepository, never()).save(any());
    }

    @Test
    void addStockForShop_storeNotFoundThrowsException() {
        Stock stock = new Stock("item", 10, 20.0, 0, Collections.emptyList());
        Authentication auth = mockAuth(OWNER_ID, false);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class,
                () -> storeService.addStockForShop(STORE_ID, stock, auth));

        assertNotNull(ex.getMessage());
        verify(storeRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private StoreProfile makeStore(String id, String ownerId) {
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "name", "shortname", "address",
                "https://image.url", "081mobilenumb",
                Collections.singletonList("tag"),
                null, ownerId, new Bank());
        store.setId(id);
        Bank bank = new Bank();
        bank.setAccountId("bankAccId");
        store.setBank(bank);
        return store;
    }

    /**
     * Creates a mock Authentication.
     * Both stubs use lenient() because some code paths (admin check short-circuits getName(),
     * store-not-found throws before any auth check) will not invoke every stub.
     */
    private Authentication mockAuth(String principalId, boolean isAdmin) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(principalId);
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        if (isAdmin) {
            authorities.add(() -> "ROLE_ADMIN");
        }
        lenient().doReturn(authorities).when(auth).getAuthorities();
        return auth;
    }
}
