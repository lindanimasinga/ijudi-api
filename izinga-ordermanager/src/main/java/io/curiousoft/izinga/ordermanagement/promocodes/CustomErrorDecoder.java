package io.curiousoft.izinga.ordermanagement.promocodes;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomErrorDecoder implements ErrorDecoder {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            var responseBody = mapper.readTree(response.body().asInputStream());
            return new Exception(responseBody.get("message").asText());
        } catch (IOException e) {
            return new RuntimeException(e);
        }
    }
}