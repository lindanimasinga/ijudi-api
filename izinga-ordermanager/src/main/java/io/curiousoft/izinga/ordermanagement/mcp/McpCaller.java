package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.CallerIdentityValidator;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ADR-026 D2: Thin facade over the per-request caller-identity state populated by
 * {@link io.curiousoft.izinga.ordermanagement.security.StoreScopeValidationFilter}.
 *
 * <p>Controllers use this facade to access caller context rather than reading ThreadLocals
 * directly. This decouples controllers from the specific ThreadLocal implementation and
 * replaces the three duplicated {@code resolveCallerUserId()} prefix-loop implementations
 * currently in {@code OrderServiceImpl}, {@code ReconServiceImpl}, and
 * {@code AiCustomerServiceAgent} with a single implementation here.</p>
 *
 * <h3>Phase 0 scope</h3>
 * <p>In Phase 0 no controllers exist yet, so this class is defined but not yet called
 * from production code. It becomes the dependency of Phase 1+ controllers. Its unit
 * tests run in Phase 0.</p>
 *
 * <h3>Key properties</h3>
 * <ul>
 *   <li>{@link #getAudience()} — typed audience from the scope JWT</li>
 *   <li>{@link #getCallerPhone()} — raw callerPhone claim from the scope JWT</li>
 *   <li>{@link #getPermittedStoreId()} — permitted store scope from the JWT</li>
 *   <li>{@link #resolveUserId()} — request-cached phone-to-userId lookup (lazy).
 *       Replaces the three duplicated prefix-loop implementations across services.</li>
 * </ul>
 *
 * <p>The facade is NOT a Spring bean. Each controller creates it via
 * {@link #McpCaller(UserProfileRepo)} when needed, or is passed it as a constructor
 * parameter (the preferred approach for testability). Both are equally valid — no
 * shared state across requests because the ThreadLocals are per-request.</p>
 */
public final class McpCaller {

    private static final Logger LOG = LoggerFactory.getLogger(McpCaller.class);

    private final UserProfileRepo userProfileRepo;

    // Single-threaded by design — McpCaller is created per tool call; no cross-thread sharing.
    /** Lazy-resolved userId from the callerPhone. Null until first call to {@link #resolveUserId()}. */
    private String cachedUserId;
    private boolean userIdResolved;

    /**
     * Create a new {@code McpCaller} bound to the given repository for phone-to-userId lookups.
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
     * <p>The result is cached for the lifetime of this {@code McpCaller} instance
     * (i.e. for the duration of a single tool call in Phase 1+).</p>
     *
     * @return the caller's user ID, or {@code null} if callerPhone is absent,
     *         shorter than 9 digits, or no matching profile exists
     */
    public String resolveUserId() {
        if (!userIdResolved) {
            userIdResolved = true;
            cachedUserId = doResolveUserId();
        }
        return cachedUserId;
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
