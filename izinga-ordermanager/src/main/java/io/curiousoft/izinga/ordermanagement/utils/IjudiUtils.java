package io.curiousoft.izinga.ordermanagement.utils;

import io.curiousoft.izinga.commons.model.GeoPointImpl;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.ShipingGeoData;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.messaging.domain.directions.Leg;
import io.curiousoft.izinga.messaging.firebase.GoogleServices;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Java port of the Kotlin utility methods from IjudiUtils.kt
 */
public final class IjudiUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(IjudiUtils.class);

    private IjudiUtils() { /* utility */ }

    /**
     * Validate South African ID number format and Luhn check.
     */
    public static boolean isIdNumber(String id) {
        if (id == null) return false;
        final String idNumberRegex = "(((\\d{2}((0[13578]|1[02])(0[1-9]|[12]\\d|3[01])|(0[13456789]|1[012])(0[1-9]|[12]\\d|30)|02(0[1-9]|1\\d|2[0-8])))|([02468][048]|[13579][26])0229))(( |-)(\\d{4})( |-)(\\d{3})|(\\d{7}))";
        if (!id.matches(idNumberRegex)) return false;

        // Luhn validation: typical Luhn algorithm on full id
        try {
            int sum = 0;
            boolean alternate = false;
            for (int i = id.length() - 1; i >= 0; i--) {
                char ch = id.charAt(i);
                if (!Character.isDigit(ch)) continue; // skip separators if present
                int n = ch - '0';
                if (alternate) {
                    n *= 2;
                    if (n > 9) n = n - 9;
                }
                sum += n;
                alternate = !alternate;
            }
            return sum % 10 == 0;
        } catch (Exception ex) {
            LOGGER.debug("Error during Luhn validation", ex);
            return false;
        }
    }

    public static boolean isSAMobileNumber(String number) {
        if (number == null) return false;
        final String regex = "(\\+27|27|0)[1-9]\\d{8}";
        return number.matches(regex);
    }

    /**
     * Calculates markup price keeping original decimals/cents similar to Kotlin version.
     */
    public static double calculateMarkupPrice(double storePrice, double markPercentage) {
        double cents = storePrice - (int) storePrice;
        double markupPrice = storePrice + storePrice * markPercentage;
        return (int) markupPrice + (cents > 0.45 ? cents : 1 + cents);
    }

    /**
     * Uses Google Maps APIs (via GoogleServices.GoogleMaps) to compute driving distance in km and return ShipingGeoData.
     * Note: this method may throw runtime exceptions from network/retrofit calls; callers should handle them.
     */
    @SneakyThrows
    public static ShipingGeoData calculateDrivingDirectionKM(String apiKey, Order order, StoreProfile store) {
        GoogleServices.GoogleMaps googleMapsInstance = GoogleServices.GoogleMaps.instance;

        // from lat long
        String fromLatLong;
        Boolean deliversMultiple = null;
        try {
            // try to use getter name convention; fall back gracefully
            deliversMultiple = (Boolean) safeGet(store, "getDeliversFromMultipleAddresses", "isDeliversFromMultipleAddresses");
        } catch (Exception ignored) {
        }

        if (Boolean.TRUE.equals(deliversMultiple) && order.getShippingData() != null) {
            var geocodeResp = googleMapsInstance.geocodeAddress(apiKey, order.getShippingData().getFromAddress(), 100.0).execute().body();
            var loc = geocodeResp.getResults().get(0).getGeometry().getLocation();
            fromLatLong = String.format(Locale.US, "%s,%s", loc.getLat(), loc.getLng());
        } else {
            // fallback to store coords
            fromLatLong = String.format(Locale.US, "%s,%s", store.getLatitude(), store.getLongitude());
        }

        // to lat long
        var geoCode = googleMapsInstance.geocodeAddress(apiKey, order.getShippingData().getToAddress(), 100.0).execute().body();
        var location = geoCode.getResults().get(0).getGeometry().getLocation();
        String toLatLong = String.format(Locale.US, "%s,%s", location.getLat(), location.getLng());

        var directions = googleMapsInstance.findDirections(apiKey, fromLatLong, toLatLong).execute().body();

        // find minimum leg distance across all routes
        int minMeters = Integer.MAX_VALUE;
        if (directions != null && directions.getRoutes() != null) {
            for (var route : directions.getRoutes()) {
                if (route.getLegs() == null) continue;
                for (Leg leg : route.getLegs()) {
                    if (leg.getDistance() != null && leg.getDistance().getValue() >= 0) {
                        minMeters = Math.min(minMeters, leg.getDistance().getValue());
                    }
                }
            }
        }
        double distanceKm = (minMeters == Integer.MAX_VALUE) ? 0.0 : ((double) minMeters) / 1000.0;

        GeoPointImpl fromGeo = new GeoPointImpl(Double.parseDouble(fromLatLong.split(",")[0]), Double.parseDouble(fromLatLong.split(",")[1]));
        GeoPointImpl toGeo = new GeoPointImpl(location.getLat(), location.getLng());

        return new ShipingGeoData(fromGeo, toGeo, distanceKm);
    }

    public static double calculateDeliveryFee(double standardFee, double standardDistance, double ratePerKM, double distance) {
        return distance > standardDistance ? standardFee + ratePerKM * (distance - standardDistance) : standardFee;
    }

    public static boolean isNullOrEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    // reflection helper: try to call getter methods to detect boolean flag; returns null if not found
    private static Object safeGet(Object target, String primary, String secondary) {
        try {
            var m = target.getClass().getMethod(primary);
            return m.invoke(target);
        } catch (Exception e) {
            try {
                var m2 = target.getClass().getMethod(secondary);
                return m2.invoke(target);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}

