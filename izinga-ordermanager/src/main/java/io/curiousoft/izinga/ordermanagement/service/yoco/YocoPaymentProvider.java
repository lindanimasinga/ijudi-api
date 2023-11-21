package io.curiousoft.izinga.ordermanagement.service.yoco;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.PaymentType;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.ordermanagement.service.PaymentProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static java.lang.String.format;

@Service
public class YocoPaymentProvider extends PaymentProvider<YocoPaymentData> {

    private static final Logger logger = Logger.getLogger(YocoPaymentProvider.class.getName());
    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper mapper;


    @Autowired
    public YocoPaymentProvider(@Value("${yoco.api.url}") String baseUrl,
                               @Value("${yoco.api.key}") String apiKey, ObjectMapper mapper) {
        super(PaymentType.YOCO);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.mapper = mapper;
    }

    @Override
    protected boolean paymentReceived(Order order) throws Exception {
        var computed = checksum(format("%s%s%s", order.getId(),order.getTotalAmount(), order.getCustomerId()));
        return Arrays.stream(Objects.requireNonNull(order.getDescription())
                .split(":"))
                .filter(i -> i.startsWith("yoco-"))
                .findFirst()
                .map(i -> i.replace("yoco-", ""))
                .map(i -> i.equals(computed)).orElse(false);
    }

    String checksum(String data) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("MD5")
                .digest(format("%s%s", data, apiKey).getBytes());
        return new String(Base64.getEncoder().encode(digest));
    }

    @Override
    public boolean makePaymentToShop(YocoPaymentData paymentData) throws Exception {
        return false;
    }

    @Override
    public boolean makePaymentToShop(StoreProfile store, Order order, double basketAmountExclFees) throws Exception {
        return false;//ukheshePaymentProvider.makePaymentToShop(order, basketAmountExclFees);
    }

    @Override
    public void makePayments(List<Order> ordersList) {

    }

    @Override
    public void makePaymentToMessenger(Order order, double amount) throws Exception {
        //ukheshePaymentProvider.makePaymentToMessenger(order, amount);
    }

    @Override
    public boolean reversePayment(Order order) throws JsonProcessingException {
        String url = baseUrl + "/refunds/";
        RestTemplate rest = new RestTemplateBuilder()
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .defaultHeader("X-Auth-Secret-Key", apiKey)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-type", "application/json");
        //Create a new HttpEntity
        String chargeId = order.getDescription().split("\\|")[1].replace("charge-", "");
        YocoReverseRequest body = new YocoReverseRequest(chargeId);
        final HttpEntity<YocoReverseRequest> entity = new HttpEntity<>(body, headers);
        ResponseEntity<YocoPaymentResponse> response;
        try {
            response = rest.exchange(url, HttpMethod.POST, entity, YocoPaymentResponse.class);
        } catch (HttpClientErrorException.BadRequest e) {
            YocoErrorResponse error = mapper.readValue(e.getResponseBodyAsString(), YocoErrorResponse.class);
            return  "refund_already_processed".equalsIgnoreCase(error.getErrorCode());
        }
        return response.getBody() != null && "successful".equalsIgnoreCase(response.getBody().getStatus());
    }
}
