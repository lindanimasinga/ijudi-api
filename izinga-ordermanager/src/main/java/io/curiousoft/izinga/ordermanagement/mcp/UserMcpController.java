package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityValidator;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Collections;
import java.util.List;

/**
 * ADR-026 Phase 2 MCP controller for user-profile tools.
 *
 * <p>Owns the three user tools extracted from {@link UserProfileService}:
 * {@code find_user_by_phone}, {@code find_users}, and {@code get_missing_fields_by_phone}.
 * Controller-layer concerns (identity gates, audience restrictions, and audit writes)
 * are handled here before delegating to the service layer.</p>
 *
 * <p>create_user removed from the MCP surface (ADR-026 Phase 2b, Lindani 2026-11-05);
 * user creation is REST/internal only.</p>
 *
 * <h3>Gate ordering per tool (ADR-026 F1)</h3>
 * <ul>
 *   <li>find_user_by_phone: rejectTokenlessMcp (throws WITHOUT audit) →
 *       validateCallerPhone (audited on violation)</li>
 *   <li>find_users: validateMcpAudience("ADMIN") — catches violation, writes audit, rethrows</li>
 *   <li>get_missing_fields_by_phone: same gate as find_user_by_phone (reproduces it
 *       exactly, as the service's getAllMissingFields delegates to findUserByPhone)</li>
 * </ul>
 *
 * <h3>R5 compliance</h3>
 * <p>This class depends on {@link UserProfileService} (a service) and
 * {@link AgentIdentityAuditService}. It never depends on any {@code *Repo} or
 * {@code *Repository} type directly.</p>
 */
@McpToolController
public class UserMcpController {

    private final UserProfileService userProfileService;
    private final AgentIdentityAuditService agentIdentityAuditService;

    public UserMcpController(UserProfileService userProfileService,
                              AgentIdentityAuditService agentIdentityAuditService) {
        this.userProfileService = userProfileService;
        this.agentIdentityAuditService = agentIdentityAuditService;
    }

    // ---- find_user_by_phone ----------------------------------------------------------------

    /**
     * Finds a user profile by phone number.
     *
     * <p>Gate 1: {@link CallerIdentityValidator#rejectTokenlessMcp} — hard-reject tokenless MCP
     * requests BEFORE any input-dependent branch (the phone-length guard) so a tokenless caller
     * cannot distinguish a short phone (returns null) from IDENTITY_REFUSAL.  No audit is written
     * at this stage.
     *
     * <p>Gate 2: {@link CallerIdentityValidator#validateCallerPhone} — CUSTOMER and DRIVER
     * audiences may only look up their own phone. Writes an async audit record on violation.
     * STORE, ADMIN, and OTP audiences are unrestricted.
     */
    @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(
        name = "find_user_by_phone",
        description = "Finds a user profile by phone number. It will try to find the userby adding different country code prefixes to the phone number provided."
    )
    public UserProfile findUserByPhone(String phone) {
        // Gate 1: reject tokenless MCP requests FIRST — no audit written here
        // (SEC-MCP-01 / SEC-MCP-BUG-01): must fire before the phone.length < 9 guard
        // so a tokenless caller cannot get a distinguishable null response.
        CallerIdentityValidator.rejectTokenlessMcp("find_user_by_phone");
        if (phone == null || phone.length() < 9) {
            return null;
        }
        // Gate 2: enforce phone ownership; audit on violation
        try {
            CallerIdentityValidator.validateCallerPhone(phone);
        } catch (CallerIdentityViolationException e) {
            // FIX-04: capture audience on the request thread before the @Async audit dispatch
            String auditAudience = CallerIdentityContext.getAudience() != null
                    ? CallerIdentityContext.getAudience() : "UNKNOWN";
            agentIdentityAuditService.recordViolation(
                    CallerIdentityContext.getCallerPhone(), phone,
                    AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH, "find_user_by_phone",
                    auditAudience, null, null);
            throw e;
        }
        return userProfileService.findUserByPhone(phone);
    }

    // ---- find_users -------------------------------------------------------------------------

    /**
     * Finds users by role and location.
     *
     * <p>Gate: {@link CallerIdentityValidator#validateMcpAudience} with "ADMIN" — catches
     * violation, writes async audit record, then rethrows.
     * AMD-01 (REQ-A6): ADMIN is the only permitted audience for find_users.
     */
    @McpAudience({Audience.ADMIN})
    @Tool(
        name = "find_users",
        description = "finds users by role and location. It will return a list of user profiles that match the specified role and are within the specified range of the given latitude and longitude."
    )
    public List<UserProfile> findUsers(
            @ToolParam(description = "This will always have a value MESSENGER because this serach is only allow to search drivers available") ProfileRoles role,
            double latitude,
            double longitude,
            @ToolParam(description = "range is in degrees. 1 degree is approximately 111 km. So a range of 0.01 would be approximately 1.11 km") double range,
            StoreType storeType) {
        // Gate: ADMIN-only audience check with audit write on violation
        try {
            CallerIdentityValidator.validateMcpAudience("ADMIN");
        } catch (CallerIdentityViolationException e) {
            // FIX-04: capture audience on the request thread before the @Async audit dispatch
            String auditAudience = CallerIdentityContext.getAudience() != null
                    ? CallerIdentityContext.getAudience() : "UNKNOWN";
            agentIdentityAuditService.recordViolation(
                    CallerIdentityContext.getCallerPhone(), role.name(),
                    AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION, "find_users",
                    auditAudience, null, null);
            throw e;
        }
        return userProfileService.findByLocation(role, latitude, longitude, range, storeType);
    }

    // ---- get_missing_fields_by_phone -------------------------------------------------------

    /**
     * Returns a list of missing mandatory field names for the user profile associated
     * with the given mobile number.
     *
     * <p>Gate: same as {@link #findUserByPhone} — rejectTokenlessMcp (no audit) →
     * validateCallerPhone (audited on violation). The original Kotlin implementation delegates
     * to {@code findUserByPhone} which carries the gate; after Phase 2 the service's
     * {@code findUserByPhone} is ungated, so the gate is reproduced here.
     */
    @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(
        name = "get_missing_fields_by_phone",
        description = "Returns a list of missing mandatory field names for the user profile associated with the given mobile number."
    )
    public List<String> getMissingFieldsByPhone(String mobileNumber) {
        // Gate 1: reject tokenless MCP requests FIRST — no audit written here
        CallerIdentityValidator.rejectTokenlessMcp("get_missing_fields_by_phone");
        if (mobileNumber == null || mobileNumber.length() < 9) {
            return Collections.emptyList();
        }
        // Gate 2: enforce phone ownership; audit on violation
        try {
            CallerIdentityValidator.validateCallerPhone(mobileNumber);
        } catch (CallerIdentityViolationException e) {
            // FIX-04: capture audience on the request thread before the @Async audit dispatch
            String auditAudience = CallerIdentityContext.getAudience() != null
                    ? CallerIdentityContext.getAudience() : "UNKNOWN";
            agentIdentityAuditService.recordViolation(
                    CallerIdentityContext.getCallerPhone(), mobileNumber,
                    AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH, "get_missing_fields_by_phone",
                    auditAudience, null, null);
            throw e;
        }
        return userProfileService.getAllMissingFields(mobileNumber);
    }
}
