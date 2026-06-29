package io.curiousoft.izinga.ordermanagement.service.paymentverify.yoco;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.PaymentType;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentProvider;
import lombok.SneakyThrows;
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

import static java.lang.String.format;

@Service
public class YocoPaymentProvider extends PaymentProvider<YocoPaymentData> {

    private static final Logger logger = Logger.getLogger(YocoPaymentProvider.class.getName());
    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final String izingaUrl;


    @Autowired
    public YocoPaymentProvider(@Value("${yoco.api.url}") String baseUrl,
                               @Value("${yoco.api.key}") String apiKey,
                               @Value("${yoco.verifier.order-manager-url}") String izingaUrl,
                               ObjectMapper mapper) {
        super(PaymentType.YOCO);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.mapper = mapper;
        this.izingaUrl = izingaUrl;
    }

    @Override
    protected boolean paymentReceived(Order order) {
        var computed = checksum(format("%s%s%s", order.getId(),order.getTotalAmount(), order.getCustomerId()));
        return Arrays.stream(Objects.requireNonNull(order.getDescription())
                .split(":"))
                .filter(i -> i.startsWith("yoco-"))
                .findFirst()
                .map(i -> i.replace("yoco-", ""))
                .map(i -> i.equals(computed)).orElse(false);
    }

    @SneakyThrows
    String checksum(String data) {
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
    public boolean reversePayment(Order order) {
        String url = izingaUrl + "/refund/initiate";
        RestTemplate rest = new RestTemplateBuilder()
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .defaultHeader("X-Auth-Secret-Key", apiKey)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-type", "application/json");
        //Create a new HttpEntity
        String checkoutId = order.getTag().get("yoco-checkout-id");
        YocoReverseRequest body = new YocoReverseRequest(checkoutId);
        final HttpEntity<YocoReverseRequest> entity = new HttpEntity<>(body, headers);
        ResponseEntity response;
        try {
            response = rest.exchange(url, HttpMethod.POST, entity, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.BadRequest e) {
            e.printStackTrace();
        }
        return  false;
    }
}
