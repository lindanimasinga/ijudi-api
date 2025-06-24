package io.curiousoft.izinga.messaging.whatsapp;

import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class WhatsAppFactory {

    private static Logger LOGGER = LoggerFactory.getLogger(WhatsAppFactory.class);

    public static WhatsAppService createWhatsappService(String token) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(30, TimeUnit.SECONDS);
        builder.connectTimeout(30, TimeUnit.SECONDS);
        builder.addInterceptor(chain -> chain.proceed(chain.request()
                .newBuilder()
                .url(new HttpUrl.Builder()
                        .scheme("https")
                        .host("graph.facebook.com")
                        .encodedPath(chain.request().url().encodedPath())
                        .build())
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
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
        return new Retrofit.Builder()
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("https://graph.facebook.com/v23.0/")
                .build()
                .create(WhatsAppService.class);
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
