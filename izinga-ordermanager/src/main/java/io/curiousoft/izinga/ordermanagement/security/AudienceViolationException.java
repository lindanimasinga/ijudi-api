package io.curiousoft.izinga.ordermanagement.security;

/**
 * Thrown when an MCP tool annotated with {@link RequiresAudience} is invoked
 * from a request whose scope token carries an incompatible audience.
 */
public class AudienceViolationException extends RuntimeException {

    public AudienceViolationException(String message) {
        super(message);
    }
}
