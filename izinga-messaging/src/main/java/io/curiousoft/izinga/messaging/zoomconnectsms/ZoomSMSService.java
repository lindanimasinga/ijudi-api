package io.curiousoft.izinga.messaging.zoomconnectsms;

import io.curiousoft.izinga.messaging.domain.ZoomResponse;
import io.curiousoft.izinga.messaging.domain.ZoomSMSMessage;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ZoomSMSService {

    String ZOOM_API_URL = "https://www.zoomconnect.com/app/api/rest/v1/";

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("sms/send")
    Call<ZoomResponse> sendSMS(@Body ZoomSMSMessage zoomSMSMessage);
}
