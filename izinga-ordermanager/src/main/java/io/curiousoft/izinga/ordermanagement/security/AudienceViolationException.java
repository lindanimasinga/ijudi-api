package io.curiousoft.izinga.ordermanagement.security;

/**
 * Thrown when an MCP {@code @Tool} method's {@link StoreScopeValidator#validateAudience} call
 * detects that the request's scope token carries an incompatible (or absent) audience.
 *
 * <p>ADR-026 Phase 6: {@code @RequiresAudience} annotation retired. Audience enforcement is
 * now done explicitly in each controller method via {@link StoreScopeValidator#validateAudience}.</p>
 */
public class AudienceViolationException extends RuntimeException {

    public AudienceViolationException(String message) {
        super(message);
    }
}
