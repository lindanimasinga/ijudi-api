package io.curiousoft.izinga.messaging.whatsapp;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface WhatsAppService {

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("{phoneId}/messages")
    Call<WhatsappTemplateResponse> sendMessage(@Path ("phoneId") String phoneId, @Body WhatsappTemplateRequest whatsappMessage);
}
