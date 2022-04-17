package io.curiousoft.ijudi.ordermanagement.service.yoco;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.service.PaymentProvider;
import io.curiousoft.ijudi.ordermanagement.service.ukheshe.UkheshePaymentProvider;
import io.curiousoft.ijudi.ordermanagement.utils.IjudiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Logger;

@Service
public class YocoPaymentProvider extends PaymentProvider<YocoPaymentData> {

    private static Logger logger = Logger.getLogger(YocoPaymentProvider.class.getName());
    private final String apiKey;
    private final String baseUrl;


    @Autowired
    public YocoPaymentProvider(@Value("${yoco.api.url}") String baseUrl,
                               @Value("${yoco.api.key}") String apiKey) {
        super(PaymentType.YOCO);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    protected boolean paymentReceived(Order order) throws Exception {
        String token = order.getDescription().replace("yoco-", "");
        String url = baseUrl + "/charges/";
        URI uri = new URI(url);
        RestTemplate rest = new RestTemplateBuilder()
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .defaultHeader("X-Auth-Secret-Key", apiKey)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-type", "application/json");
        //Create a new HttpEntity
        YocoPayRequest body = new YocoPayRequest(token, (int) (order.getTotalAmount() * 100), "ZAR" );
        final HttpEntity<YocoPayRequest> entity = new HttpEntity<>(body, headers);
        ResponseEntity<YocoPaymentResponse> response = rest.exchange(url, HttpMethod.POST, entity, YocoPaymentResponse.class);
        boolean isSuccessful = response.getBody() != null && "successful".equalsIgnoreCase(response.getBody().getStatus());
        if(isSuccessful) {
            String descr = order.getDescription() + "|charge-" + response.getBody().getId();
            order.setDescription(descr);
        }
        return isSuccessful;
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
        String token = order.getDescription().replace("yoco-", "");
        String url = baseUrl + "/charges/";
        RestTemplate rest = new RestTemplateBuilder()
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .defaultHeader("X-Auth-Secret-Key", apiKey)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-type", "application/json");
        //Create a new HttpEntity
        String chargeId = order.getDescription().split("\\|")[0].replace("charge:", "");
        YocoReverseRequest body = new YocoReverseRequest(chargeId);
        final HttpEntity<YocoReverseRequest> entity = new HttpEntity<>(body, headers);
        ResponseEntity<YocoPaymentResponse> response = rest.exchange(url, HttpMethod.POST, entity, YocoPaymentResponse.class);
        return response.getBody() != null && "successful".equalsIgnoreCase(response.getBody().getStatus());
    }
}
