package io.curiousoft.izinga.ordermanagement.utils;

/**
 * Thrown when the Google Maps service is unavailable (transport/IO failure or non-2xx response)
 * and a driving-distance calculation cannot be completed.
 *
 * <p>Distinct from {@link IllegalArgumentException}, which is thrown for unresolvable addresses
 * (a caller-input problem). This exception signals a transient infrastructure outage.</p>
 *
 * <p>Controllers map this to HTTP 503 Service Unavailable.</p>
 */
public class DeliveryDistanceUnavailableException extends RuntimeException {

    public DeliveryDistanceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public DeliveryDistanceUnavailableException(String message) {
        super(message);
    }
}
