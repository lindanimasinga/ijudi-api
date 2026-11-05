package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * ADR-026 D2: Unit tests for {@link McpCaller} with 100% branch coverage.
 *
 * Tests verify:
 * - resolveUserId() returns null when callerPhone is null
 * - resolveUserId() returns null when callerPhone is shorter than 9 chars
 * - resolveUserId() tries prefixes 0, +27, 27 and returns the first match
 * - resolveUserId() returns null when no prefix finds a match
 * - resolveUserId() resolves per-call (NOTE-01: no instance caching; repo called each time)
 * - getAudience() returns StoreScopeContext.getAudience()
 * - getCallerPhone() returns StoreScopeContext.getCallerPhone()
 * - getPermittedStoreId() returns StoreScopeContext.getPermittedStoreId()
 * - null userProfileRepo throws IllegalArgumentException
 */
@ExtendWith(MockitoExtension.class)
class McpCallerTest {

    @Mock private UserProfileRepo userProfileRepo;

    private static final String CALLER_PHONE = "+27812345678";
    private static final String CALLER_LAST9 = "812345678";
    private static final String CALLER_ID = "user-id-123";
    private static final String PERMITTED_STORE = "store-id-456";

    @BeforeEach
    void setUp() {
        StoreScopeContext.clear();
    }

    @AfterEach
    void tearDown() {
        StoreScopeContext.clear();
    }

    // ---- Constructor ----

    @Test
    void constructor_nullRepo_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new McpCaller(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void constructor_validRepo_succeeds() {
        McpCaller caller = new McpCaller(userProfileRepo);
        assertThat(caller).isNotNull();
    }

    // ---- resolveUserId ----

    @Test
    void resolveUserId_callerPhoneNull_returnsNull() {
        // No caller phone set in context
        McpCaller caller = new McpCaller(userProfileRepo);

        String result = caller.resolveUserId();

        assertThat(result).isNull();
        // No repo call should be made
        verify(userProfileRepo, never()).findByMobileNumber(any());
    }

    @Test
    void resolveUserId_callerPhoneTooShort_returnsNull() {
        StoreScopeContext.setCallerPhone("12345");  // less than 9 chars

        McpCaller caller = new McpCaller(userProfileRepo);

        String result = caller.resolveUserId();

        assertThat(result).isNull();
        verify(userProfileRepo, never()).findByMobileNumber(any());
    }

    @Test
    void resolveUserId_foundWithPrefix0_returnsUserId() {
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        UserProfile profile = profileWithId(CALLER_ID);
        // First prefix "0" matches
        when(userProfileRepo.findByMobileNumber("0" + CALLER_LAST9)).thenReturn(profile);

        McpCaller caller = new McpCaller(userProfileRepo);

        String result = caller.resolveUserId();

        assertThat(result).isEqualTo(CALLER_ID);
        verify(userProfileRepo).findByMobileNumber("0" + CALLER_LAST9);
        // Does not try further prefixes
        verify(userProfileRepo, never()).findByMobileNumber("+27" + CALLER_LAST9);
        verify(userProfileRepo, never()).findByMobileNumber("27" + CALLER_LAST9);
    }

    @Test
    void resolveUserId_notFoundWithPrefix0_foundWithPlus27_returnsUserId() {
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        UserProfile profile = profileWithId(CALLER_ID);
        when(userProfileRepo.findByMobileNumber("0" + CALLER_LAST9)).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27" + CALLER_LAST9)).thenReturn(profile);

        McpCaller caller = new McpCaller(userProfileRepo);

        String result = caller.resolveUserId();

        assertThat(result).isEqualTo(CALLER_ID);
        verify(userProfileRepo).findByMobileNumber("0" + CALLER_LAST9);
        verify(userProfileRepo).findByMobileNumber("+27" + CALLER_LAST9);
        verify(userProfileRepo, never()).findByMobileNumber("27" + CALLER_LAST9);
    }

    @Test
    void resolveUserId_foundWithPrefix27_returnsUserId() {
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        UserProfile profile = profileWithId(CALLER_ID);
        when(userProfileRepo.findByMobileNumber("0" + CALLER_LAST9)).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27" + CALLER_LAST9)).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("27" + CALLER_LAST9)).thenReturn(profile);

        McpCaller caller = new McpCaller(userProfileRepo);

        String result = caller.resolveUserId();

        assertThat(result).isEqualTo(CALLER_ID);
        verify(userProfileRepo).findByMobileNumber("0" + CALLER_LAST9);
        verify(userProfileRepo).findByMobileNumber("+27" + CALLER_LAST9);
        verify(userProfileRepo).findByMobileNumber("27" + CALLER_LAST9);
    }

    @Test
    void resolveUserId_noMatchAnyPrefix_returnsNull() {
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);

        McpCaller caller = new McpCaller(userProfileRepo);

        String result = caller.resolveUserId();

        assertThat(result).isNull();
        // All 3 prefixes were tried
        verify(userProfileRepo).findByMobileNumber("0" + CALLER_LAST9);
        verify(userProfileRepo).findByMobileNumber("+27" + CALLER_LAST9);
        verify(userProfileRepo).findByMobileNumber("27" + CALLER_LAST9);
    }

    @Test
    void resolveUserId_calledTwice_repoCalledTwice_noCaching() {
        // NOTE-01: McpCaller is now a Spring singleton — instance-field caching removed to prevent
        // cross-request leakage between concurrent threads. resolveUserId() resolves per call.
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        UserProfile profile = profileWithId(CALLER_ID);
        when(userProfileRepo.findByMobileNumber("0" + CALLER_LAST9)).thenReturn(profile);

        McpCaller caller = new McpCaller(userProfileRepo);

        String result1 = caller.resolveUserId();
        String result2 = caller.resolveUserId();

        assertThat(result1).isEqualTo(CALLER_ID);
        assertThat(result2).isEqualTo(CALLER_ID);
        // Repo called twice — no instance caching (per-call resolution)
        verify(userProfileRepo, times(2)).findByMobileNumber("0" + CALLER_LAST9);
    }

    // ---- getAudience ----

    @Test
    void getAudience_whenContextHasAudience_returnsAudience() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);

        McpCaller caller = new McpCaller(userProfileRepo);

        assertThat(caller.getAudience()).isEqualTo(Audience.CUSTOMER);
    }

    @Test
    void getAudience_whenContextHasNoAudience_returnsNull() {
        // No audience set
        McpCaller caller = new McpCaller(userProfileRepo);

        assertThat(caller.getAudience()).isNull();
    }

    @Test
    void getAudience_allAudienceValues_returnsCorrectly() {
        for (Audience audience : Audience.values()) {
            StoreScopeContext.clear();
            StoreScopeContext.setAudience(audience);

            McpCaller caller = new McpCaller(userProfileRepo);

            assertThat(caller.getAudience()).isEqualTo(audience);
        }
    }

    // ---- getCallerPhone ----

    @Test
    void getCallerPhone_whenContextHasPhone_returnsPhone() {
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        McpCaller caller = new McpCaller(userProfileRepo);

        assertThat(caller.getCallerPhone()).isEqualTo(CALLER_PHONE);
    }

    @Test
    void getCallerPhone_whenContextHasNoPhone_returnsNull() {
        McpCaller caller = new McpCaller(userProfileRepo);

        assertThat(caller.getCallerPhone()).isNull();
    }

    // ---- getPermittedStoreId ----

    @Test
    void getPermittedStoreId_whenContextHasStoreId_returnsStoreId() {
        StoreScopeContext.setPermittedStoreId(PERMITTED_STORE);

        McpCaller caller = new McpCaller(userProfileRepo);

        assertThat(caller.getPermittedStoreId()).isEqualTo(PERMITTED_STORE);
    }

    @Test
    void getPermittedStoreId_whenContextHasNoStoreId_returnsNull() {
        McpCaller caller = new McpCaller(userProfileRepo);

        assertThat(caller.getPermittedStoreId()).isNull();
    }

    // ---- isMcpRequest ----

    @Test
    void isMcpRequest_whenNotSet_returnsFalse() {
        McpCaller caller = new McpCaller(userProfileRepo);
        assertThat(caller.isMcpRequest()).isFalse();
    }

    @Test
    void isMcpRequest_whenSet_returnsTrue() {
        io.curiousoft.izinga.commons.security.CallerIdentityContext.setMcpRequest(true);
        try {
            McpCaller caller = new McpCaller(userProfileRepo);
            assertThat(caller.isMcpRequest()).isTrue();
        } finally {
            io.curiousoft.izinga.commons.security.CallerIdentityContext.clear();
        }
    }

    // ---- Helper ----

    private UserProfile profileWithId(String id) {
        UserProfile p = new UserProfile(
                "Test User",
                UserProfile.SignUpReason.BUY,
                "1 Test St",
                "https://img/test.png",
                CALLER_PHONE,
                ProfileRoles.CUSTOMER
        );
        p.setId(id);
        return p;
    }
}
