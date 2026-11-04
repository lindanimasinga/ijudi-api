package io.curiousoft.izinga.ordermanagement.utils;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.messaging.domain.directions.Distance;
import io.curiousoft.izinga.messaging.domain.directions.GoogleDirectionsResponse;
import io.curiousoft.izinga.messaging.domain.directions.Leg;
import io.curiousoft.izinga.messaging.domain.directions.Route;
import io.curiousoft.izinga.messaging.domain.geofencing.Geometry;
import io.curiousoft.izinga.messaging.domain.geofencing.GoogleGeoCodeResponse;
import io.curiousoft.izinga.messaging.domain.geofencing.Location;
import io.curiousoft.izinga.messaging.domain.geofencing.Result;
import io.curiousoft.izinga.messaging.firebase.GoogleServices;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IjudiUtils#calculateDrivingDirectionKM(String, Order, StoreProfile, GoogleServices.GoogleMaps)}.
 * Uses the package-private overload that accepts an injectable Maps client to avoid live network calls
 * and to sidestep Java 17 static-final reflection restrictions on {@code GoogleServices.GoogleMaps.instance}.
 *
 * <p>IMPORTANT: never call stub-setup methods (geocodeCallWith/directionsCallWith) inside a
 * thenReturn() argument — Mockito's strict-stubbing detector treats that as UnfinishedStubbing.
 * Always build the Call stub first, then pass it to thenReturn().
 */
@RunWith(MockitoJUnitRunner.class)
public class IjudiUtilsTest {

    private static final String API_KEY = "test-api-key";

    @Mock
    private GoogleServices.GoogleMaps mapsClient;

    @SuppressWarnings("deprecation")
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    // -----------------------------------------------------------------------
    // Helper builders — must be called BEFORE the outer when().thenReturn()
    // -----------------------------------------------------------------------

    private StoreProfile buildStore(boolean deliversFromMultiple) {
        Bank bank = new Bank();
        StoreProfile store = new StoreProfile(
                StoreType.FOOD,
                "Test Store", "shortname", "123 Test Street, Durban",
                "https://image.url", "0811234567",
                Collections.singletonList("test"),
                new ArrayList<>(), "ownerId", bank);
        store.setLatitude(-29.78);
        store.setLongitude(31.00);
        store.setDeliversFromMultipleAddresses(deliversFromMultiple);
        return store;
    }

    private Order buildOrder(String fromAddress, String toAddress) {
        Order order = new Order();
        ShippingData shipping = new ShippingData(fromAddress, toAddress, ShippingData.ShippingType.DELIVERY);
        order.setShippingData(shipping);
        return order;
    }

    /** Returns a Call mock that executes to a success response wrapping the given body (may be null). */
    @SuppressWarnings("unchecked")
    private Call<GoogleGeoCodeResponse> geocodeCall(GoogleGeoCodeResponse body) throws Exception {
        Call<GoogleGeoCodeResponse> call = mock(Call.class);
        // Response.success() is called first so no nested when() happens inside another when().
        Response<GoogleGeoCodeResponse> resp = Response.success(body);
        when(call.execute()).thenReturn(resp);
        return call;
    }

    @SuppressWarnings("unchecked")
    private Call<GoogleDirectionsResponse> directionsCall(GoogleDirectionsResponse body) throws Exception {
        Call<GoogleDirectionsResponse> call = mock(Call.class);
        Response<GoogleDirectionsResponse> resp = Response.success(body);
        when(call.execute()).thenReturn(resp);
        return call;
    }

    private GoogleGeoCodeResponse geoResponseWithLocation(double lat, double lng) {
        Location location = new Location(lat, lng);
        Geometry geometry = new Geometry(location, "ROOFTOP", null);
        Result result = new Result(Collections.emptyList(), "Some Address", geometry, "place123", Collections.emptyList());
        return new GoogleGeoCodeResponse(Collections.singletonList(result), "OK");
    }

    private GoogleDirectionsResponse directionsResponseWithDistance(int meters) {
        Distance distance = new Distance();
        distance.setValue(meters);
        Leg leg = new Leg();
        leg.setDistance(distance);
        Route route = new Route();
        route.setLegs(Collections.singletonList(leg));
        GoogleDirectionsResponse resp = new GoogleDirectionsResponse();
        resp.setRoutes(Collections.singletonList(route));
        return resp;
    }

    // -----------------------------------------------------------------------
    // FIX-01: FROM-address geocode failure for deliversFromMultipleAddresses stores
    // -----------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_fromAddressGeocodeReturnsNullBody_throwsIllegalArgumentException()
            throws Exception {
        // given: store delivers from multiple addresses; FROM geocode body is null
        StoreProfile store = buildStore(true);
        Order order = buildOrder("Unknown From Address", "21 Mgobhozi Rd, Durban");

        // Build the Call stub first, then pass to thenReturn.
        Call<GoogleGeoCodeResponse> fromGeoCall = geocodeCall(null);
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("Unknown From Address"), anyDouble()))
                .thenReturn(fromGeoCall);

        // expect
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("could not find the pickup (from) address");

        // when
        IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store, mapsClient);
    }

    @Test
    public void calculateDrivingDirectionKM_fromAddressGeocodeReturnsEmptyResults_throwsIllegalArgumentException()
            throws Exception {
        // given: store delivers from multiple addresses; FROM geocode returns empty result list
        StoreProfile store = buildStore(true);
        Order order = buildOrder("Bad From Address", "21 Mgobhozi Rd, Durban");

        GoogleGeoCodeResponse emptyResp = new GoogleGeoCodeResponse(Collections.emptyList(), "ZERO_RESULTS");
        Call<GoogleGeoCodeResponse> fromGeoCall = geocodeCall(emptyResp);
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("Bad From Address"), anyDouble()))
                .thenReturn(fromGeoCall);

        // expect
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("could not find the pickup (from) address");

        // when
        IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store, mapsClient);
    }

    // -----------------------------------------------------------------------
    // TO-address geocode failure (all store types)
    // -----------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_toAddressGeocodeReturnsNullBody_throwsIllegalArgumentException()
            throws Exception {
        // given: standard store (no deliversFromMultiple); TO geocode body is null
        StoreProfile store = buildStore(false);
        Order order = buildOrder("123 Test St", "Unknown To Address");

        // Only the TO geocode is called for a standard store
        Call<GoogleGeoCodeResponse> toGeoCall = geocodeCall(null);
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("Unknown To Address"), anyDouble()))
                .thenReturn(toGeoCall);

        // expect
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Unable to calculate delivery distance for the provided address");

        // when
        IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store, mapsClient);
    }

    @Test
    public void calculateDrivingDirectionKM_toAddressGeocodeReturnsEmptyResults_throwsIllegalArgumentException()
            throws Exception {
        StoreProfile store = buildStore(false);
        Order order = buildOrder("123 Test St", "Bad To Address");

        GoogleGeoCodeResponse emptyResp = new GoogleGeoCodeResponse(Collections.emptyList(), "ZERO_RESULTS");
        Call<GoogleGeoCodeResponse> toGeoCall = geocodeCall(emptyResp);
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("Bad To Address"), anyDouble()))
                .thenReturn(toGeoCall);

        // expect
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Unable to calculate delivery distance for the provided address");

        // when
        IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store, mapsClient);
    }

    // -----------------------------------------------------------------------
    // Directions response failure (null body → minMeters stays MAX_VALUE)
    // -----------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_directionsReturnsNullBody_throwsIllegalArgumentException()
            throws Exception {
        StoreProfile store = buildStore(false);
        Order order = buildOrder("123 Test St", "456 Delivery Rd");

        Call<GoogleGeoCodeResponse> toGeoCall = geocodeCall(geoResponseWithLocation(-29.785, 31.001));
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("456 Delivery Rd"), anyDouble()))
                .thenReturn(toGeoCall);

        Call<GoogleDirectionsResponse> dirsCall = directionsCall(null);
        when(mapsClient.findDirections(eq(API_KEY), anyString(), anyString()))
                .thenReturn(dirsCall);

        // expect
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("no route found");

        // when
        IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store, mapsClient);
    }

    @Test
    public void calculateDrivingDirectionKM_directionsReturnsEmptyRoutes_throwsIllegalArgumentException()
            throws Exception {
        StoreProfile store = buildStore(false);
        Order order = buildOrder("123 Test St", "456 Delivery Rd");

        Call<GoogleGeoCodeResponse> toGeoCall = geocodeCall(geoResponseWithLocation(-29.785, 31.001));
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("456 Delivery Rd"), anyDouble()))
                .thenReturn(toGeoCall);

        GoogleDirectionsResponse noRoutes = new GoogleDirectionsResponse();
        noRoutes.setRoutes(Collections.emptyList());
        Call<GoogleDirectionsResponse> dirsCall = directionsCall(noRoutes);
        when(mapsClient.findDirections(eq(API_KEY), anyString(), anyString()))
                .thenReturn(dirsCall);

        // expect
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("no route found");

        // when
        IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store, mapsClient);
    }

    // -----------------------------------------------------------------------
    // Happy path: standard store (store lat/lng used as from)
    // -----------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_standardStore_happyPath_returnsCorrectGeoData() throws Exception {
        // given: standard store, 1500 m distance
        StoreProfile store = buildStore(false);
        Order order = buildOrder("123 Test St", "456 Delivery Rd");

        Call<GoogleGeoCodeResponse> toGeoCall = geocodeCall(geoResponseWithLocation(-29.785, 31.001));
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("456 Delivery Rd"), anyDouble()))
                .thenReturn(toGeoCall);

        Call<GoogleDirectionsResponse> dirsCall = directionsCall(directionsResponseWithDistance(1500));
        when(mapsClient.findDirections(eq(API_KEY), anyString(), anyString()))
                .thenReturn(dirsCall);

        // when
        ShipingGeoData result = IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store, mapsClient);

        // then
        assertNotNull(result);
        assertEquals(1.5, result.getDistance(), 0.001);
    }

    // -----------------------------------------------------------------------
    // Happy path: deliversFromMultiple store, FROM geocode succeeds
    // -----------------------------------------------------------------------

    @Test
    public void calculateDrivingDirectionKM_multipleAddressStore_fromGeocodesSuccessfully_happyPath()
            throws Exception {
        StoreProfile store = buildStore(true);
        Order order = buildOrder("From Warehouse, Durban", "456 Delivery Rd");

        // FROM geocode succeeds
        Call<GoogleGeoCodeResponse> fromGeoCall = geocodeCall(geoResponseWithLocation(-29.780, 30.999));
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("From Warehouse, Durban"), anyDouble()))
                .thenReturn(fromGeoCall);

        // TO geocode succeeds
        Call<GoogleGeoCodeResponse> toGeoCall = geocodeCall(geoResponseWithLocation(-29.785, 31.001));
        when(mapsClient.geocodeAddress(eq(API_KEY), eq("456 Delivery Rd"), anyDouble()))
                .thenReturn(toGeoCall);

        // Directions succeed: 2000 m
        Call<GoogleDirectionsResponse> dirsCall = directionsCall(directionsResponseWithDistance(2000));
        when(mapsClient.findDirections(eq(API_KEY), anyString(), anyString()))
                .thenReturn(dirsCall);

        ShipingGeoData result = IjudiUtils.calculateDrivingDirectionKM(API_KEY, order, store, mapsClient);

        assertNotNull(result);
        assertEquals(2.0, result.getDistance(), 0.001);
    }
}
