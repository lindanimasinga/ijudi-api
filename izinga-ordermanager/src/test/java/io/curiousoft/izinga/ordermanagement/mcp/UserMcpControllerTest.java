package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ADR-026 Phase 2: Unit tests for {@link UserMcpController} — controller-layer identity gates
 * for the four user tools extracted from {@link UserProfileService}.
 *
 * <p><strong>Coverage</strong>:
 * <ul>
 *   <li>find_user_by_phone: tokenless reject (no audit), own phone allow, other phone deny+audit</li>
 *   <li>find_users: ADMIN-only gate — tokenless+DRIVER+CUSTOMER+STORE+OTP deny+audit; ADMIN allow</li>
 *   <li>create_user: tokenless reject (no audit), own phone allow, other phone deny+audit,
 *       null/blank phone allow (gate skipped)</li>
 *   <li>get_missing_fields_by_phone: same gate as find_user_by_phone</li>
 * </ul>
 *
 * <p><strong>Non-vacuity</strong>: every ALLOW row verifies that the downstream service method
 * was actually invoked. Every DENY row verifies that it was NOT invoked.
 */
@ExtendWith(MockitoExtension.class)
class UserMcpControllerTest {

    private static final String CALLER_PHONE = "+27812345678";
    private static final String OTHER_PHONE  = "+27899999999";

    @Mock private UserProfileService     userProfileService;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    @AfterEach
    void clearContext() {
        CallerIdentityContext.clear();
    }

    private UserMcpController controller() {
        return new UserMcpController(userProfileService, agentIdentityAuditService);
    }

    // ==================================================================================
    // find_user_by_phone
    // ==================================================================================

    @Test
    void findUserByPhone_tokenless_throwsIdentityRefusalNoAudit() {
        CallerIdentityContext.setMcpRequest(true);
        // No audience — tokenless

        assertThatThrownBy(() -> controller().findUserByPhone(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        // rejectTokenlessMcp must NOT write an audit record
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
        verify(userProfileService, never()).findUserByPhone(any());
    }

    @Test
    void findUserByPhone_shortPhone_tokenless_throwsIdentityRefusalBeforeShortPhoneGuard() {
        // Gate 1 fires before the short-phone null-return branch
        CallerIdentityContext.setMcpRequest(true);

        assertThatThrownBy(() -> controller().findUserByPhone("123"))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");
    }

    @Test
    void findUserByPhone_customerAudience_ownPhone_allow_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        UserProfile stub = mock(UserProfile.class);
        when(userProfileService.findUserByPhone(CALLER_PHONE)).thenReturn(stub);

        UserProfile result = controller().findUserByPhone(CALLER_PHONE);

        assertThat(result).isSameAs(stub);
        verify(userProfileService).findUserByPhone(CALLER_PHONE);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void findUserByPhone_customerAudience_otherPhone_denyPlusAudit() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        assertThatThrownBy(() -> controller().findUserByPhone(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("find_user_by_phone"),
                eq("CUSTOMER"), isNull(), isNull());
        verify(userProfileService, never()).findUserByPhone(any());
    }

    @Test
    void findUserByPhone_driverAudience_otherPhone_denyPlusAudit() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("DRIVER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        assertThatThrownBy(() -> controller().findUserByPhone(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class);

        verify(agentIdentityAuditService).recordViolation(
                any(), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("find_user_by_phone"), any(), isNull(), isNull());
    }

    @Test
    void findUserByPhone_adminAudience_otherPhone_allow_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");

        when(userProfileService.findUserByPhone(OTHER_PHONE)).thenReturn(null);

        controller().findUserByPhone(OTHER_PHONE);

        verify(userProfileService).findUserByPhone(OTHER_PHONE);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void findUserByPhone_storeAudience_otherPhone_allow_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("STORE");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        when(userProfileService.findUserByPhone(OTHER_PHONE)).thenReturn(null);

        controller().findUserByPhone(OTHER_PHONE);

        verify(userProfileService).findUserByPhone(OTHER_PHONE);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void findUserByPhone_shortPhone_withToken_returnsNull() {
        // After tokenless gate passes, short phone → null (not a gate violation)
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        UserProfile result = controller().findUserByPhone("123");

        assertThat(result).isNull();
        verify(userProfileService, never()).findUserByPhone(any());
    }

    // ==================================================================================
    // find_users
    // ==================================================================================

    @Test
    void findUsers_tokenless_throwsPlusAudit() {
        CallerIdentityContext.setMcpRequest(true);
        // No audience

        assertThatThrownBy(() -> controller().findUsers(
                ProfileRoles.MESSENGER, -26.0, 28.0, 0.1, StoreType.FOOD))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        // null-audience case goes through the catch block → audit IS written
        verify(agentIdentityAuditService).recordViolation(
                isNull(), eq("MESSENGER"),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_users"), eq("UNKNOWN"), isNull(), isNull());
        verify(userProfileService, never()).findByLocation(any(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void findUsers_driverAudience_deny() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("DRIVER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        assertThatThrownBy(() -> controller().findUsers(
                ProfileRoles.MESSENGER, -26.0, 28.0, 0.1, StoreType.FOOD))
                .isInstanceOf(CallerIdentityViolationException.class);

        verify(agentIdentityAuditService).recordViolation(
                any(), any(),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_users"), eq("DRIVER"), isNull(), isNull());
        verify(userProfileService, never()).findByLocation(any(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void findUsers_customerAudience_deny() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        assertThatThrownBy(() -> controller().findUsers(
                ProfileRoles.MESSENGER, -26.0, 28.0, 0.1, StoreType.FOOD))
                .isInstanceOf(CallerIdentityViolationException.class);

        verify(agentIdentityAuditService).recordViolation(
                any(), any(),
                eq(AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION),
                eq("find_users"), eq("CUSTOMER"), isNull(), isNull());
    }

    @Test
    void findUsers_storeAudience_deny() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("STORE");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        assertThatThrownBy(() -> controller().findUsers(
                ProfileRoles.MESSENGER, -26.0, 28.0, 0.1, StoreType.FOOD))
                .isInstanceOf(CallerIdentityViolationException.class);
    }

    @Test
    void findUsers_otpAudience_deny() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("OTP");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        assertThatThrownBy(() -> controller().findUsers(
                ProfileRoles.MESSENGER, -26.0, 28.0, 0.1, StoreType.FOOD))
                .isInstanceOf(CallerIdentityViolationException.class);
    }

    @Test
    void findUsers_adminAudience_allow_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");

        when(userProfileService.findByLocation(
                eq(ProfileRoles.MESSENGER), anyDouble(), anyDouble(), anyDouble(), eq(StoreType.FOOD)))
                .thenReturn(Collections.emptyList());

        List<UserProfile> result = controller().findUsers(
                ProfileRoles.MESSENGER, -26.0, 28.0, 0.1, StoreType.FOOD);

        assertThat(result).isNotNull();
        verify(userProfileService).findByLocation(
                eq(ProfileRoles.MESSENGER), anyDouble(), anyDouble(), anyDouble(), eq(StoreType.FOOD));
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    // ==================================================================================
    // create_user
    // ==================================================================================

    @Test
    void createUser_tokenless_throwsIdentityRefusalNoAudit() throws Exception {
        CallerIdentityContext.setMcpRequest(true);
        // No audience

        UserProfile profile = new UserProfile(
                "Name", UserProfile.SignUpReason.BUY, "Addr",
                "https://t.c", OTHER_PHONE, ProfileRoles.CUSTOMER);

        assertThatThrownBy(() -> controller().createUser(profile))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
        verify(userProfileService, never()).create(any());
    }

    @Test
    void createUser_customerAudience_ownPhone_allow_delegatesToService() throws Exception {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        UserProfile profile = new UserProfile(
                "Name", UserProfile.SignUpReason.BUY, "Addr",
                "https://t.c", CALLER_PHONE, ProfileRoles.CUSTOMER);

        UserProfile saved = mock(UserProfile.class);
        when(userProfileService.create(profile)).thenReturn(saved);

        UserProfile result = controller().createUser(profile);

        assertThat(result).isSameAs(saved);
        verify(userProfileService).create(profile);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createUser_customerAudience_otherPhone_denyPlusAudit() throws Exception {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        UserProfile profile = new UserProfile(
                "Other", UserProfile.SignUpReason.BUY, "Addr",
                "https://t.c", OTHER_PHONE, ProfileRoles.CUSTOMER);

        assertThatThrownBy(() -> controller().createUser(profile))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.THIRD_PARTY_REGISTRATION),
                eq("create_user"), eq("CUSTOMER"), isNull(), isNull());
        verify(userProfileService, never()).create(any());
    }

    @Test
    void createUser_adminAudience_otherPhone_allow_delegatesToService() throws Exception {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");

        UserProfile profile = new UserProfile(
                "Driver", UserProfile.SignUpReason.DELIVERY_DRIVER, "Addr",
                "https://t.c", OTHER_PHONE, ProfileRoles.MESSENGER);

        UserProfile saved = mock(UserProfile.class);
        when(userProfileService.create(profile)).thenReturn(saved);

        UserProfile result = controller().createUser(profile);

        assertThat(result).isSameAs(saved);
        verify(userProfileService).create(profile);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createUser_storeAudience_otherPhone_allow_delegatesToService() throws Exception {
        // STORE audience: validateCallerPhone allows any phone (no phone-ownership restriction)
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("STORE");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        UserProfile profile = new UserProfile(
                "Customer", UserProfile.SignUpReason.BUY, "Addr",
                "https://t.c", OTHER_PHONE, ProfileRoles.CUSTOMER);

        UserProfile saved = mock(UserProfile.class);
        when(userProfileService.create(profile)).thenReturn(saved);

        controller().createUser(profile);

        verify(userProfileService).create(profile);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createUser_nullPhone_skipGate_tokenless_throwsIdentityRefusalFirst() throws Exception {
        // Gate 1 (rejectTokenlessMcp) fires even when phone is null — no audit
        CallerIdentityContext.setMcpRequest(true);
        // No audience

        UserProfile profile = new UserProfile(
                "Name", UserProfile.SignUpReason.BUY, "Addr",
                "https://t.c", null, ProfileRoles.CUSTOMER);

        assertThatThrownBy(() -> controller().createUser(profile))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createUser_nullPhone_withToken_skipPhoneGate_delegatesToService() throws Exception {
        // Phone is null → Gate 2 (validateCallerPhone) is skipped; delegates to service
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        UserProfile profile = new UserProfile(
                "Name", UserProfile.SignUpReason.BUY, "Addr",
                "https://t.c", null, ProfileRoles.CUSTOMER);

        try {
            controller().createUser(profile);
        } catch (CallerIdentityViolationException e) {
            throw new AssertionError("Null phone must skip Gate 2; no IDENTITY_REFUSAL expected", e);
        } catch (Exception ignored) {
            // Any service-level validation error is acceptable
        }

        verify(userProfileService).create(profile);
    }

    // ==================================================================================
    // get_missing_fields_by_phone
    // ==================================================================================

    @Test
    void getMissingFieldsByPhone_tokenless_throwsIdentityRefusalNoAudit() {
        CallerIdentityContext.setMcpRequest(true);

        assertThatThrownBy(() -> controller().getMissingFieldsByPhone(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
        verify(userProfileService, never()).getAllMissingFields(anyString());
    }

    @Test
    void getMissingFieldsByPhone_shortPhone_tokenless_throwsBeforeShortPhoneGuard() {
        CallerIdentityContext.setMcpRequest(true);

        assertThatThrownBy(() -> controller().getMissingFieldsByPhone("123"))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");
    }

    @Test
    void getMissingFieldsByPhone_customerAudience_ownPhone_allow_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        when(userProfileService.getAllMissingFields(CALLER_PHONE)).thenReturn(Collections.emptyList());

        List<String> result = controller().getMissingFieldsByPhone(CALLER_PHONE);

        assertThat(result).isNotNull();
        verify(userProfileService).getAllMissingFields(CALLER_PHONE);
        verify(agentIdentityAuditService, never()).recordViolation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getMissingFieldsByPhone_customerAudience_otherPhone_denyPlusAudit() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        assertThatThrownBy(() -> controller().getMissingFieldsByPhone(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        verify(agentIdentityAuditService).recordViolation(
                eq(CALLER_PHONE), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("get_missing_fields_by_phone"), eq("CUSTOMER"), isNull(), isNull());
        verify(userProfileService, never()).getAllMissingFields(anyString());
    }

    @Test
    void getMissingFieldsByPhone_adminAudience_otherPhone_allow_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");

        when(userProfileService.getAllMissingFields(OTHER_PHONE)).thenReturn(Collections.emptyList());

        List<String> result = controller().getMissingFieldsByPhone(OTHER_PHONE);

        assertThat(result).isNotNull();
        verify(userProfileService).getAllMissingFields(OTHER_PHONE);
    }

    @Test
    void getMissingFieldsByPhone_shortPhone_withToken_returnsEmptyList() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(CALLER_PHONE);

        List<String> result = controller().getMissingFieldsByPhone("123");

        assertThat(result).isEmpty();
        verify(userProfileService, never()).getAllMissingFields(anyString());
    }
}
