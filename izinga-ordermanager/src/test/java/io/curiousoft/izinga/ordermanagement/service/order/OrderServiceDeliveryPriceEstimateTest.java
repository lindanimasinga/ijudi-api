package io.curiousoft.izinga.ordermanagement.service.order;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.AdminOnlyNotificationService;
import io.curiousoft.izinga.commons.order.DeliveryPriceEstimateDto;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.orders.RestrictedRegionService;
import io.curiousoft.izinga.ordermanagement.orders.quote.OrderQuoteRepository;
import io.curiousoft.izinga.ordermanagement.promocodes.PromoCodeClient;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentService;
import io.curiousoft.izinga.ordermanagement.utils.IjudiUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderServiceDeliveryPriceEstimateTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserProfileRepo userProfileRepo;
    @Mock
    private PaymentService paymentService;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private FirebaseNotificationService firebaseNotificationService;
    @Mock
    private AdminOnlyNotificationService adminOnlyNotificationService;
    @Mock
    private EmailNotificationService emailNotificationService;
    @Mock
    private PromoCodeClient promoCodeClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private RestrictedRegionService restrictedRegionService;
    @Mock
    private OrderQuoteRepository quoteRepository;

    private OrderServiceImpl sut;

    @BeforeEach
    public void setUp() {
        sut = new OrderServiceImpl(
                15.0,
                3.0,
                5.0,
                0.025,
                0.10,
                5,
                List.of("08128155660"),
                "google-key",
                orderRepository,
                storeRepository,
                userProfileRepo,
                paymentService,
                deviceRepository,
                firebaseNotificationService,
                adminOnlyNotificationService,
                emailNotificationService,
                promoCodeClient,
                eventPublisher,
                restrictedRegionService,
                quoteRepository
        );
    }

    @Test
    public void getDeliveryPriceEstimate_throwsWhenCategoryMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> sut.getDeliveryPriceEstimate(" ", "A", "B", null));
    }

    @Test
    public void getDeliveryPriceEstimate_throwsWhenFromAddressMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> sut.getDeliveryPriceEstimate("Truck Delivery Driver", " ", "B", null));
    }

    @Test
    public void getDeliveryPriceEstimate_throwsWhenToAddressMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> sut.getDeliveryPriceEstimate("Truck Delivery Driver", "A", " ", null));
    }

    @Test
    public void getDeliveryPriceEstimate_throwsWhenShopIdProvidedButMissing() {
        when(storeRepository.findById("missing-shop")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> sut.getDeliveryPriceEstimate("Truck Delivery Driver", "A", "B", "missing-shop"));
    }

    @Test
    public void getDeliveryPriceEstimate_returnsEstimateWithGlobalDefaults_whenShopIdNotProvided() {
        ShipingGeoData geoData = new ShipingGeoData(
                new GeoPointImpl(-29.0, 31.0),
                new GeoPointImpl(-29.1, 31.1),
                5.0
        );

        try (MockedStatic<IjudiUtils> ijudiUtils = mockStatic(IjudiUtils.class, CALLS_REAL_METHODS)) {
            ijudiUtils.when(() -> IjudiUtils.calculateDrivingDirectionKM(anyString(), any(Order.class), any(StoreProfile.class)))
                    .thenReturn(geoData);

            DeliveryPriceEstimateDto result = sut.getDeliveryPriceEstimate("Truck Delivery Driver", "From A", "To B", null);

            assertNotNull(result);
            assertEquals("Truck Delivery Driver", result.getCategory());
            assertEquals("From A", result.getFromAddress());
            assertEquals("To B", result.getToAddress());
            assertEquals(5.0, result.getDistanceKm(), 0.0);
            assertEquals(15.0, result.getStandardFee(), 0.0);
            assertEquals(3.0, result.getStandardKm(), 0.0);
            assertEquals(5.0, result.getRatePerKm(), 0.0);
            assertEquals(25.0, result.getEstimatedDeliveryFee(), 0.0);
        }
    }

    @Test
    public void getDeliveryPriceEstimate_returnsEstimateUsingShopRates_whenShopIdProvided() {
        StoreProfile storeProfile = OrderServiceTest.createStoreProfile(StoreType.MOVERS);
        Rates rates = new Rates();
        rates.setStandardDeliveryPriceTruck(100.0);
        rates.setStandardDeliveryKm(2.0);
        rates.setRatePerKm(10.0);
        storeProfile.setRates(rates);

        when(storeRepository.findById("shop-1")).thenReturn(Optional.of(storeProfile));

        ShipingGeoData geoData = new ShipingGeoData(
                new GeoPointImpl(-29.0, 31.0),
                new GeoPointImpl(-29.1, 31.1),
                5.0
        );

        try (MockedStatic<IjudiUtils> ijudiUtils = mockStatic(IjudiUtils.class, CALLS_REAL_METHODS)) {
            ijudiUtils.when(() -> IjudiUtils.calculateDrivingDirectionKM(anyString(), any(Order.class), any(StoreProfile.class)))
                    .thenReturn(geoData);

            DeliveryPriceEstimateDto result = sut.getDeliveryPriceEstimate("Truck Delivery Driver", "From A", "To B", "shop-1");

            assertNotNull(result);
            assertEquals(100.0, result.getStandardFee(), 0.0);
            assertEquals(2.0, result.getStandardKm(), 0.0);
            assertEquals(10.0, result.getRatePerKm(), 0.0);
            assertEquals(130.0, result.getEstimatedDeliveryFee(), 0.0);
            verify(storeRepository).findById("shop-1");
        }
    }

    @Test
    public void getDeliveryPriceEstimate_usesVehicleSpecificRatePerKm_whenConfigured() {
        StoreProfile storeProfile = OrderServiceTest.createStoreProfile(StoreType.MOVERS);
        Rates rates = new Rates();
        rates.setStandardDeliveryPriceTruck(100.0);
        rates.setStandardDeliveryKm(2.0);
        rates.setRatePerKm(10.0);
        rates.setRatePerKmTruck(20.0);
        storeProfile.setRates(rates);

        when(storeRepository.findById("shop-1")).thenReturn(Optional.of(storeProfile));

        ShipingGeoData geoData = new ShipingGeoData(
                new GeoPointImpl(-29.0, 31.0),
                new GeoPointImpl(-29.1, 31.1),
                5.0
        );

        try (MockedStatic<IjudiUtils> ijudiUtils = mockStatic(IjudiUtils.class, CALLS_REAL_METHODS)) {
            ijudiUtils.when(() -> IjudiUtils.calculateDrivingDirectionKM(anyString(), any(Order.class), any(StoreProfile.class)))
                    .thenReturn(geoData);

            DeliveryPriceEstimateDto result = sut.getDeliveryPriceEstimate("Truck Delivery Driver", "From A", "To B", "shop-1");

            assertNotNull(result);
            assertEquals(20.0, result.getRatePerKm(), 0.0);
            assertEquals(160.0, result.getEstimatedDeliveryFee(), 0.0);
        }
    }

    @Test
    public void getDeliveryPriceEstimate_fallsBackToGeneralRatePerKm_whenVehicleSpecificRateMissing() {
        StoreProfile storeProfile = OrderServiceTest.createStoreProfile(StoreType.MOVERS);
        Rates rates = new Rates();
        rates.setStandardDeliveryPriceTruck(100.0);
        rates.setStandardDeliveryKm(2.0);
        rates.setRatePerKm(10.0);
        storeProfile.setRates(rates);

        when(storeRepository.findById("shop-1")).thenReturn(Optional.of(storeProfile));

        ShipingGeoData geoData = new ShipingGeoData(
                new GeoPointImpl(-29.0, 31.0),
                new GeoPointImpl(-29.1, 31.1),
                5.0
        );

        try (MockedStatic<IjudiUtils> ijudiUtils = mockStatic(IjudiUtils.class, CALLS_REAL_METHODS)) {
            ijudiUtils.when(() -> IjudiUtils.calculateDrivingDirectionKM(anyString(), any(Order.class), any(StoreProfile.class)))
                    .thenReturn(geoData);

            DeliveryPriceEstimateDto result = sut.getDeliveryPriceEstimate("Truck Delivery Driver", "From A", "To B", "shop-1");

            assertNotNull(result);
            assertEquals(10.0, result.getRatePerKm(), 0.0);
            assertEquals(130.0, result.getEstimatedDeliveryFee(), 0.0);
        }
    }
}
