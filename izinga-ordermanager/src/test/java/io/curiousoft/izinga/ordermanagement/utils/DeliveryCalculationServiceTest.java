package io.curiousoft.izinga.ordermanagement.utils;

import io.curiousoft.izinga.commons.model.Bank;
import io.curiousoft.izinga.commons.model.GeoPointImpl;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.ShipingGeoData;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.StoreType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collections;

/**
 * Unit tests for {@link DeliveryCalculationService}.
 *
 * <p>Uses {@link MockedStatic} to intercept the static
 * {@link IjudiUtils#calculateDrivingDirectionKM(String, Order, StoreProfile)} call without live
 * network calls. These are plain unit tests (no Spring context), so MockedStatic is safe here.</p>
 *
 * <p>Verified behaviours:
 * <ul>
 *   <li>Happy path: successful result is returned unchanged.</li>
 *   <li>IAE pass-through: {@link IllegalArgumentException} from IjudiUtils propagates as-is.</li>
 *   <li>Outage wrapping: {@link IOException} (sneaky-thrown) is caught and wrapped in
 *       {@link DeliveryDistanceUnavailableException}.</li>
 *   <li>Outage wrapping: any other non-IAE {@link RuntimeException} is also wrapped.</li>
 * </ul>
 * </p>
 */
@RunWith(MockitoJUnitRunner.class)
public class DeliveryCalculationServiceTest {

    private static final String API_KEY = "test-api-key";

    private final DeliveryCalculationService sut = new DeliveryCalculationService(API_KEY);

    /** Build a minimal StoreProfile using the required all-args constructor. */
    private static StoreProfile buildStore() {
        return new StoreProfile(
                StoreType.FOOD,
                "Test Store", "shortname", "address",
                "https://image.url", "0812345678",
                Collections.singletonList("tag"),
                Collections.emptyList(),
                "ownerId",
                new Bank());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_successfulResult_isReturnedUnchanged() {
        ShipingGeoData expected = new ShipingGeoData(
                new GeoPointImpl(-29.78, 30.99),
                new GeoPointImpl(-29.80, 31.01),
                3.5);
        Order order = new Order();
        StoreProfile store = buildStore();

        try (MockedStatic<IjudiUtils> mocked = Mockito.mockStatic(IjudiUtils.class)) {
            mocked.when(() -> IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store))
                  .thenReturn(expected);

            ShipingGeoData result = sut.calculateDrivingDirectionKM(order, store);

            Assert.assertSame(expected, result);
        }
    }

    // -------------------------------------------------------------------------
    // IAE pass-through: unresolvable address — must NOT be wrapped
    // -------------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_illegalArgumentException_propagatesUnchanged() {
        IllegalArgumentException original = new IllegalArgumentException(
                "Unable to calculate delivery distance for the provided address. " +
                "Please check the address and try again.");
        Order order = new Order();
        StoreProfile store = buildStore();

        try (MockedStatic<IjudiUtils> mocked = Mockito.mockStatic(IjudiUtils.class)) {
            mocked.when(() -> IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store))
                  .thenThrow(original);

            try {
                sut.calculateDrivingDirectionKM(order, store);
                Assert.fail("Expected IllegalArgumentException to propagate");
            } catch (IllegalArgumentException e) {
                Assert.assertSame("IAE instance must be the original, not a wrapper", original, e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Outage wrapping: IOException sneaky-thrown by IjudiUtils (@SneakyThrows)
    // -------------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_ioException_wrappedAsDeliveryDistanceUnavailableException() {
        // Simulates a Retrofit Call.execute() IOException sneaky-thrown through @SneakyThrows.
        IOException ioException = new IOException("Connection refused");
        Order order = new Order();
        StoreProfile store = buildStore();

        try (MockedStatic<IjudiUtils> mocked = Mockito.mockStatic(IjudiUtils.class)) {
            // @SneakyThrows makes the IOException an unchecked throw at the JVM level; in
            // MockedStatic we throw it via thenAnswer to bypass the checked-exception limitation.
            mocked.when(() -> IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store))
                  .thenAnswer(inv -> { throw ioException; });

            try {
                sut.calculateDrivingDirectionKM(order, store);
                Assert.fail("Expected DeliveryDistanceUnavailableException");
            } catch (DeliveryDistanceUnavailableException e) {
                Assert.assertTrue(
                        "Message must mention maps service unavailability",
                        e.getMessage().contains("maps service is unavailable"));
                Assert.assertSame("Cause must be the original IOException", ioException, e.getCause());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Outage wrapping: generic RuntimeException (e.g. non-2xx Maps response)
    // -------------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_runtimeException_wrappedAsDeliveryDistanceUnavailableException() {
        RuntimeException networkException = new RuntimeException("HTTP 500 from Maps API");
        Order order = new Order();
        StoreProfile store = buildStore();

        try (MockedStatic<IjudiUtils> mocked = Mockito.mockStatic(IjudiUtils.class)) {
            mocked.when(() -> IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store))
                  .thenThrow(networkException);

            try {
                sut.calculateDrivingDirectionKM(order, store);
                Assert.fail("Expected DeliveryDistanceUnavailableException");
            } catch (DeliveryDistanceUnavailableException e) {
                Assert.assertTrue(
                        "Message must mention maps service unavailability",
                        e.getMessage().contains("maps service is unavailable"));
                Assert.assertSame("Cause must be the original RuntimeException", networkException, e.getCause());
            }
        }
    }
}
