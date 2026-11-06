package io.curiousoft.izinga.ordermanagement.utils;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.ShipingGeoData;
import io.curiousoft.izinga.commons.model.StoreProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Spring bean wrapper around the static IjudiUtils.calculateDrivingDirectionKM method.
 * Exists so that the Maps call can be replaced with {@code @MockBean} in integration tests
 * (Mockito's MockedStatic is thread-local and does not intercept calls on Tomcat worker threads
 * in SpringBootTest.WebEnvironment.RANDOM_PORT contexts).
 */
@Service
public class DeliveryCalculationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryCalculationService.class);

    private final String apiKey;

    public DeliveryCalculationService(@Value("${google.maps.api.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Delegates to {@link IjudiUtils#calculateDrivingDirectionKM}.
     *
     * <p>Throws {@link IllegalArgumentException} when geocoding or routing fails due to bad
     * caller-supplied addresses (the address is unresolvable — a caller-input problem).</p>
     *
     * <p>Throws {@link DeliveryDistanceUnavailableException} when a transport/IO failure or a
     * non-2xx Maps response prevents the calculation — this is a transient infrastructure outage,
     * not a caller-input problem. Note: {@code IjudiUtils} uses {@code @SneakyThrows} so the
     * {@code IOException} from {@code Call.execute()} is sneaky-thrown as an unchecked exception;
     * catching {@code Exception} here is intentional and correct.</p>
     */
    public ShipingGeoData calculateDrivingDirectionKM(Order order, StoreProfile storeProfile) {
        try {
            return IjudiUtils.calculateDrivingDirectionKM(apiKey, order, storeProfile);
        } catch (IllegalArgumentException e) {
            // Address-resolution failure — let it propagate unchanged so callers get a 400.
            throw e;
        } catch (Exception e) {
            // Sneaky-thrown IOException from Retrofit Call.execute(), or any other transport/
            // infrastructure failure.  Convert to a clean, informative exception for callers.
            LOGGER.warn("Maps service unavailable while calculating driving distance: {}", e.getMessage(), e);
            throw new DeliveryDistanceUnavailableException(
                    "Unable to calculate delivery distance right now: the maps service is unavailable. Please try again shortly.",
                    e);
        }
    }
}
