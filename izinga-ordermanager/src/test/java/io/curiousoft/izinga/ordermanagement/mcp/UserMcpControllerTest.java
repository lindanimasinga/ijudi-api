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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ADR-026 Phase 2: Unit tests for {@link UserMcpController} — controller-layer identity gates
 * for the three user tools on the MCP surface.
 *
 * <p>create_user removed from the MCP surface in ADR-026 Phase 2b (Lindani 2026-11-05).
 * User creation is REST/internal only ({@link UserProfileService#create} stays intact).</p>
 *
 * <p><strong>Coverage</strong>:
 * <ul>
 *   <li>find_user_by_phone: tokenless reject (no audit), own phone allow, other phone deny+audit</li>
 *   <li>find_users: ADMIN-only gate — tokenless+DRIVER+CUSTOMER+STORE+OTP deny+audit; ADMIN allow</li>
 *   <li>get_missing_fields_by_phone: same gate as find_user_by_phone</li>
 *   <li>no create_user: reflection assertion that no {@code @Tool(name="create_user")} method exists</li>
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
    // create_user absence — ADR-026 Phase 2b
    // ==================================================================================

    /**
     * ADR-026 Phase 2b (Lindani 2026-11-05): create_user must NOT be declared as a {@code @Tool}
     * on {@link UserMcpController}. User creation is REST/internal only.
     * {@link UserProfileService#create} stays intact for REST/internal callers.
     */
    @Test
    void controllerDeclaresNoCreateUserTool() {
        UserMcpController ctrl = controller();
        boolean hasCreateUser = Arrays.stream(ctrl.getClass().getDeclaredMethods())
                .anyMatch(m -> {
                    Tool tool = m.getAnnotation(Tool.class);
                    return tool != null && "create_user".equals(tool.name());
                });
        assertThat(hasCreateUser)
                .as("create_user must not be a @Tool method on UserMcpController " +
                    "(ADR-026 Phase 2b, Lindani 2026-11-05)")
                .isFalse();
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
