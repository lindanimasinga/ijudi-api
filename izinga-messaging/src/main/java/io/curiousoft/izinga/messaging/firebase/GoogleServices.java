package io.curiousoft.izinga.messaging.firebase;

import io.curiousoft.izinga.messaging.domain.GooglePlacesResponse;
import io.curiousoft.izinga.messaging.domain.directions.GoogleDirectionsResponse;
import io.curiousoft.izinga.messaging.domain.geofencing.GoogleGeoCodeResponse;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class GoogleServices {

    public static FirebaseMessageService getMessagingService() {
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).connectTimeout(20,
                TimeUnit.SECONDS).build();
        return new Retrofit.Builder().client(client).baseUrl("https://fcm.googleapis.com").addConverterFactory(
                GsonConverterFactory.create()).build().create(FirebaseMessageService.class);
    }

    public static FirebaseInstanceIdHttpService getInstanceIdService() {
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).connectTimeout(20,
                TimeUnit.SECONDS).build();
        return new Retrofit.Builder().client(client).baseUrl("https://iid.googleapis.com").addConverterFactory(
                GsonConverterFactory.create()).build().create(FirebaseInstanceIdHttpService.class);
    }

    public interface FirebaseMessageService {
        @POST("/v1/projects/{project}/messages:send")
        @Headers({"Content-Type: application/json"})
        Call<Map> sendMessage(@Header("Authorization") String apiKey, @Path("project") String project, @Body FCMMessage message);
    }

    public interface FirebaseInstanceIdHttpService {

        FirebaseInstanceIdHttpService httpServiceConnector = new Retrofit.Builder().baseUrl(
                "https://iid.googleapis.com").addConverterFactory(GsonConverterFactory.create()).build().create(
                FirebaseInstanceIdHttpService.class);

        @POST("/iid/v1/{deviceToken}/rel/topics/{topicName}")
        @Headers({"Content-Type: application/json"})
        Call<Map> createTopic(@Header("Authorization") String apiKey, @Path("deviceToken") String deviceToken,
                @Path("topicName") String topicName);

        @POST("/iid/v1/{deviceToken}/rel/topics/{topicName}")
        @Headers({"Content-Type: application/json"})
        Call<Map> subscribeTopic(@Header("Authorization") String apiKey, @Path("deviceToken") String deviceToken,
                @Path("topicName") String topicName);

        @POST("/iid/v1:batchRemove")
        @Headers({"Content-Type: application/json"})
        Call<Map> unSubscribeTopic(@Header("Authorization") String apiKey, @Body FCMUnSubscribeMessage message);
    }

    public interface GoogleMaps {

        GoogleMaps instance = new Retrofit.Builder().baseUrl(
                "https://maps.googleapis.com").addConverterFactory(GsonConverterFactory.create()).build().create(
                GoogleMaps.class);

        @GET("/maps/api/place/textsearch/json")
        @Headers({"Content-Type: application/json"})
        Call<GooglePlacesResponse> findPlace(@Query("key") String apikey, @Query("query") String place, @Query("radius") double radius);

        @GET("/maps/api/geocode/json")
        @Headers({"Content-Type: application/json"})
        Call<GoogleGeoCodeResponse> geocodeAddress(@Query("key") String apikey, @Query("address") String address, @Query("radius") double radius);

        @GET("/maps/api/directions/json")
        @Headers({"Content-Type: application/json"})
        Call<GoogleDirectionsResponse> findDirections(@Query("key") String apikey, @Query("origin") String origin, @Query("destination") String destination);
    }
}
