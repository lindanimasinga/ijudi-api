package io.curiousoft.ijudi.ordermanagement.utils;

import com.curiousoft.alarmsystem.messaging.domain.directions.GoogleDirectionsResponse;
import com.curiousoft.alarmsystem.messaging.domain.geofencing.GoogleGeoCodeResponse;
import com.curiousoft.alarmsystem.messaging.domain.geofencing.Location;
import com.curiousoft.alarmsystem.messaging.firebase.GoogleServices;
import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.service.zoomsms.ZoomSmsNotificationService;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;
import org.hibernate.validator.internal.constraintvalidators.hv.LuhnCheckValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class IjudiUtils {

    private static Logger LOGGER = LoggerFactory.getLogger(IjudiUtils.class);

    public static boolean isIdNumber(String id) {
        String idNumberRegex =
                "(((\\d{2}((0[13578]|1[02])(0[1-9]|[12]\\d|3[01])|(0[13456789]|1[012])(0[1-9]|[12]\\d|30)|02(0[1-9]|1\\d|2[0-8])))|([02468][048]|[13579][26])0229))(( |-)(\\d{4})( |-)(\\d{3})|(\\d{7}))";

        if(Objects.isNull(id) || !id.matches(idNumberRegex)) {
            return false;
        }

        final List<Integer> digits = new ArrayList<>();
        id.chars().forEachOrdered(item -> digits.add(Integer.parseInt(""+ (char)item)) );
        char checkSome = id.charAt(id.length() -1);
        digits.remove(id.length() -1);
        LuhnCheckValidator luhnCheckValidator = new LuhnCheckValidator();
        return luhnCheckValidator.isCheckDigitValid(digits, checkSome);
    }


    public static boolean isSAMobileNumber(String number) {
        String idNumberRegex ="(\\+27|27|0)[1-9]\\d{8}";
        return number.matches(idNumberRegex);
    }

    public static String responseAsString(Response response) {
        try {
            BufferedSource source = response.body().source();
            source.request(Long.MAX_VALUE);
            return source.buffer().clone().readUtf8();
        } catch (IOException exception) {
            return "";
        }
    }

    public static String headerAsString(Headers headers) {
        if (headers.size() == 0) return "";

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            String headerName = headers.name(i);
            builder.append("\n ").append(headerName).append(": ").append(headers.value(i));
        }
        return builder.toString();
    }

    public static String requestAsString(Request request) {
        try (Buffer buffer = new Buffer()) {
            request.newBuilder().build().body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "";
        }
    }

    public  static String generateMD5Hash(String data) throws NoSuchAlgorithmException {
        LOGGER.debug("hashing string " + data);
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(data.getBytes());
        byte[] digest = md.digest();
        return DatatypeConverter
                .printHexBinary(digest).toUpperCase();
    }

    public static double calculateDrivingDirectionKM(String apiKey, Order order, Optional<StoreProfile> storeOptional) throws java.io.IOException {
        // store lat long
        String storeLatLong = storeOptional.get().getLatitude() + "," + storeOptional.get().getLongitude();
        GoogleServices.GoogleMaps googleMapsInstance = GoogleServices.GoogleMaps.instance;
        //customer lat long
        GoogleGeoCodeResponse geoCode = googleMapsInstance.geocodeAddress(apiKey, order.getShippingData().getToAddress(), 100).execute().body();
        Location location = geoCode.getResults().get(0).getGeometry().getLocation();
        String customerLatLong = location.getLat() + "," + location.getLng();
        GoogleDirectionsResponse directions = googleMapsInstance.findDirections(apiKey, storeLatLong, customerLatLong).execute().body();
        return directions.getRoutes().get(0).getLegs().get(0).getDistance().getValue() / 1000; // kilometers
    }

    public static double calculateDeliveryFee(double standardFee, double standardDistance, double ratePerKM, double distance) {
        return distance > standardDistance? standardFee + (ratePerKM * (distance - standardDistance)) : standardFee;
    }
}
