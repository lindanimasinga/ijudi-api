package io.curiousoft.izinga.ordermanagement.conroller;

import io.curiousoft.izinga.commons.model.Bank;
import io.curiousoft.izinga.commons.model.Stock;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.ordermanagement.stores.StoreControler;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * SEC-01 controller-layer tests for StoreControler.
 *
 * Verifies:
 *  1. POST /store derives ownerId from authentication.getName(), not the request body.
 *  2. PATCH /store/{id} delegates to storeService.update(id, profile, auth).
 *  3. PATCH /store/{id}/stock delegates to storeService.addStockForShop(id, stock, auth).
 *  4. PATCH /store/{id} returns 400 when path id and body id mismatch.
 *
 * Note: @PreAuthorize and URL-level security are tested at the filter-chain level
 * (Spring Security integration). These unit tests verify the controller's own logic
 * executed after authentication has already been established.
 */
@ExtendWith(MockitoExtension.class)
class StoreControlerAuthTest {

    private StoreControler controller;

    @Mock
    private StoreService storeService;
    @Mock
    private UserProfileService userProfileService;
    @Mock
    private Authentication authentication;

    private static final String AUTHENTICATED_USER_ID = "authenticated-user-mongo-id";
    private static final String STORE_ID = "store-abc-123";

    @BeforeEach
    void setUp() {
        controller = new StoreControler(storeService, userProfileService);
        // lenient: getName() is only invoked by create() methods; update/stock methods pass
        // the Authentication object through to the service without calling getName() themselves.
        lenient().when(authentication.getName()).thenReturn(AUTHENTICATED_USER_ID);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /store  — ownerId must be derived from JWT, not body
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void create_ownerIdIsOverriddenFromAuthentication() throws Exception {
        StoreProfile requestBody = makeStore(STORE_ID, "attacker-supplied-owner-id");
        StoreProfile saved = makeStore(STORE_ID, AUTHENTICATED_USER_ID);

        when(storeService.create(any(StoreProfile.class), isNull())).thenReturn(saved);

        ResponseEntity<StoreProfile> response = controller.create(requestBody, null, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // ownerId on the body must have been overwritten before the service call
        verify(authentication).getName();
        verify(storeService).create(argThat(p -> AUTHENTICATED_USER_ID.equals(p.getOwnerId())), isNull());
    }

    @Test
    void create_referralCodeIsPassedThrough() throws Exception {
        StoreProfile requestBody = makeStore(STORE_ID, "any");
        StoreProfile saved = makeStore(STORE_ID, AUTHENTICATED_USER_ID);
        String referralCode = "REF-ABC";

        when(storeService.create(any(StoreProfile.class), eq(referralCode))).thenReturn(saved);

        ResponseEntity<StoreProfile> response = controller.create(requestBody, referralCode, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(storeService).create(any(StoreProfile.class), eq(referralCode));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /store/{id}  — delegates auth-aware service overload
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void update_delegatesToAuthAwareServiceOverload() throws Exception {
        StoreProfile incoming = makeStore(STORE_ID, AUTHENTICATED_USER_ID);
        StoreProfile saved = makeStore(STORE_ID, AUTHENTICATED_USER_ID);

        when(storeService.update(eq(STORE_ID), any(StoreProfile.class), eq(authentication))).thenReturn(saved);

        ResponseEntity<StoreProfile> response = controller.update(STORE_ID, incoming, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(storeService).update(eq(STORE_ID), eq(incoming), eq(authentication));
    }

    @Test
    void update_returns400WhenPathIdAndBodyIdMismatch() throws Exception {
        StoreProfile incoming = makeStore("different-store-id", AUTHENTICATED_USER_ID);

        ResponseEntity<StoreProfile> response = controller.update(STORE_ID, incoming, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(storeService);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /store/{id}/stock  — delegates auth-aware service overload
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void addStock_delegatesToAuthAwareServiceOverload() throws Exception {
        Stock stock = new Stock("Widget", 5, 99.0, 0, Collections.emptyList());

        // void method — no stub needed, just verify it is called
        ResponseEntity<Void> response = controller.findStockForStore(stock, STORE_ID, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(storeService).addStockForShop(eq(STORE_ID), eq(stock), eq(authentication));
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
        return store;
    }
}
