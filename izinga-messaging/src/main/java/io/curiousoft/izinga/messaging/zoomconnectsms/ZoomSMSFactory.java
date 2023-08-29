package io.curiousoft.izinga.messaging.zoomconnectsms;

import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class ZoomSMSFactory {

    private static Logger LOGGER = LoggerFactory.getLogger(ZoomSMSFactory.class);

    public static ZoomSMSService createZoomSMSService(String email, String token) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(20, TimeUnit.SECONDS);
        builder.connectTimeout(20, TimeUnit.SECONDS);
        builder.addInterceptor(chain -> chain.proceed(chain.request()
                .newBuilder()
                .url(new HttpUrl.Builder()
                        .scheme("https")
                        .host("www.zoomconnect.com")
                        .encodedPath(chain.request().url().encodedPath())
                        .addQueryParameter("email", email)
                        .addQueryParameter("token", token)
                        .build())
                .build())
        );

        builder.addInterceptor(chain -> {
            Request request = chain.request();
            LOGGER.info("Request code: " + request.url());
            LOGGER.info("Request headers: " + headerAsString(request.headers()));
            LOGGER.info("Request body:\n" + requestAsString(request));
            Response response = chain.proceed(request);
            LOGGER.info("Response code: " + response.code());
            LOGGER.info("Response headers: " + headerAsString(response.headers()));
            LOGGER.info("Response body:\n" + responseAsString(response));
            return response;
        });

        OkHttpClient client = builder.build();
        return new Retrofit.Builder().client(client).baseUrl(ZoomSMSService.ZOOM_API_URL).addConverterFactory(
                GsonConverterFactory.create()).build().create(ZoomSMSService.class);
    }

    private static String responseAsString(Response response) {
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
}
