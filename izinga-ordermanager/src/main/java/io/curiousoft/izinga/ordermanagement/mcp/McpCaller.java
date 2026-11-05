package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.CallerIdentityValidator;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ADR-026 D2: Thin facade over the per-request caller-identity state populated by
 * {@link io.curiousoft.izinga.ordermanagement.security.StoreScopeValidationFilter}.
 *
 * <p>Controllers inject this bean to access caller context rather than reading ThreadLocals
 * directly. This decouples controllers from the specific ThreadLocal implementation and
 * replaces the three duplicated {@code resolveCallerUserId()} prefix-loop implementations
 * currently in {@code OrderServiceImpl}, {@code ReconServiceImpl}, and
 * {@code AiCustomerServiceAgent} with a single implementation here.</p>
 *
 * <h3>Singleton safety (NOTE-01)</h3>
 * <p>This class is a Spring singleton {@link Component}. All state it accesses
 * ({@link StoreScopeContext}, {@link io.curiousoft.izinga.commons.security.CallerIdentityContext})
 * is stored in ThreadLocals set by {@code StoreScopeValidationFilter} on each request thread,
 * so there is no cross-request state leakage. {@link #resolveUserId()} resolves per call
 * (no instance-field caching) so that concurrent requests on different threads each see their
 * own caller's phone. Instance-field caching was removed when this class became a singleton.</p>
 *
 * <h3>Key properties</h3>
 * <ul>
 *   <li>{@link #getAudience()} — typed audience from the scope JWT</li>
 *   <li>{@link #getCallerPhone()} — raw callerPhone claim from the scope JWT</li>
 *   <li>{@link #getPermittedStoreId()} — permitted store scope from the JWT</li>
 *   <li>{@link #resolveUserId()} — per-call phone-to-userId lookup.
 *       Replaces the three duplicated prefix-loop implementations across services.</li>
 * </ul>
 */
@Component
public final class McpCaller {

    private static final Logger LOG = LoggerFactory.getLogger(McpCaller.class);

    private final UserProfileRepo userProfileRepo;

    /**
     * Create a new {@code McpCaller} bound to the given repository for phone-to-userId lookups.
     * Spring injects this bean automatically; the constructor is public for testability.
     *
     * @param userProfileRepo the user profile repository (must not be null)
     */
    public McpCaller(UserProfileRepo userProfileRepo) {
        if (userProfileRepo == null) {
            throw new IllegalArgumentException("McpCaller userProfileRepo must not be null");
        }
        this.userProfileRepo = userProfileRepo;
    }

    /**
     * Returns the typed audience from the scope JWT.
     * Returns {@code null} when no scope token was presented (tokenless call).
     *
     * @return the caller's audience, or null
     */
    public Audience getAudience() {
        return StoreScopeContext.getAudience();
    }

    /**
     * Returns the caller's phone number as extracted from the scope JWT's
     * {@code callerPhone} claim. May be null when no token was presented.
     *
     * @return the caller's phone, or null
     */
    public String getCallerPhone() {
        return StoreScopeContext.getCallerPhone();
    }

    /**
     * Returns the permitted store ID from the scope JWT.
     * Returns {@code null} when the token has no store scope (DRIVER/CUSTOMER tokens,
     * or no token at all).
     *
     * @return the permitted store ID, or null
     */
    public String getPermittedStoreId() {
        return StoreScopeContext.getPermittedStoreId();
    }

    /**
     * Returns {@code true} when the current request is an MCP request.
     * Equivalent to {@link io.curiousoft.izinga.commons.security.CallerIdentityContext#isMcpRequest()}.
     *
     * @return true if this is an MCP request thread
     */
    public boolean isMcpRequest() {
        return io.curiousoft.izinga.commons.security.CallerIdentityContext.isMcpRequest();
    }

    /**
     * ADR-026: Resolve the caller's user ID from their phone number.
     *
     * <p>Implements the same prefix-loop logic as the three duplicated
     * {@code resolveCallerUserId()} methods in {@code OrderServiceImpl},
     * {@code ReconServiceImpl}, and (a variant in) {@code AiCustomerServiceAgent}.
     * Tried prefixes in order: {@code 0}, {@code +27}, {@code 27}.</p>
     *
     * <p>Resolution is per-call (no instance caching) because this class is a singleton
     * and each concurrent request runs on a different thread with a different ThreadLocal
     * callerPhone. Instance-field caching was removed in NOTE-01 to prevent cross-request
     * cache leakage across threads sharing the same singleton instance.</p>
     *
     * @return the caller's user ID, or {@code null} if callerPhone is absent,
     *         shorter than 9 digits, or no matching profile exists
     */
    public String resolveUserId() {
        return doResolveUserId();
    }

    private String doResolveUserId() {
        String callerPhone = getCallerPhone();
        if (callerPhone == null) return null;
        String last9 = CallerIdentityValidator.normalizeLast9(callerPhone);
        if (last9 == null) return null;
        for (String prefix : List.of("0", "+27", "27")) {
            UserProfile u = userProfileRepo.findByMobileNumber(prefix + last9);
            if (u != null) {
                LOG.debug("[McpCaller] resolved callerPhone to userId via prefix={}", prefix);
                return u.getId();
            }
        }
        LOG.debug("[McpCaller] callerPhone could not be resolved to a userId (no match for any prefix)");
        return null;
    }
}
