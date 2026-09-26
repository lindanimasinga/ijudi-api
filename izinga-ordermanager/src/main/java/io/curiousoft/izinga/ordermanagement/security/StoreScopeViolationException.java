package io.curiousoft.izinga.ordermanagement.security;

/**
 * Thrown when a store-scoped MCP tool is invoked with a storeId
 * that does not match the permitted storeId in the current request's scope token.
 */
public class StoreScopeViolationException extends RuntimeException {

    public StoreScopeViolationException(String message) {
        super(message);
    }
}
