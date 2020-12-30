package io.curiousoft.ijudi.ordermanagement.service.ozow;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.service.PaymentProvider;
import io.curiousoft.ijudi.ordermanagement.service.ukheshe.UkheshePaymentProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

@Service
public class OzowPaymentProvider extends PaymentProvider<OzowPaymentData> {

    private static Logger logger = Logger.getLogger(UkheshePaymentProvider.class.getName());
    private final String apiKey;
    private final String baseUrl;
    private final UkheshePaymentProvider ukheshePaymentProvider;


    @Autowired
    public OzowPaymentProvider(@Value("${ozow.api.url}") String baseUrl,
                               @Value("${ozow.api.key}") String apiKey,
                               UkheshePaymentProvider ukheshePaymentProvider) {
        super(PaymentType.OZOW);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.ukheshePaymentProvider = ukheshePaymentProvider;
    }

    @Override
    protected boolean paymentReceived(Order order) throws Exception {
        //get transactions
        LocalDateTime fromLocalDate = LocalDateTime.ofInstant(order.getCreatedDate().toInstant(),
                ZoneId.systemDefault()).minusMinutes(10);
        Date fromDate = Date.from(fromLocalDate.atZone(ZoneId.systemDefault()).toInstant());
        String transactionId = order.getDescription().replace("ozow-", "");
        String url = baseUrl + "/GetTransaction/?siteCode=CUR-CEL-001&transactionId=" + transactionId;
        URI uri = new URI(url);
        RestTemplate rest = new RestTemplateBuilder()
                .defaultHeader("ApiKey", apiKey)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-type", "application/json");
        //Create a new HttpEntity
        final HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<OzowPaymentData> response = rest.exchange(url, HttpMethod.GET, entity, OzowPaymentData.class);
        return response.getBody() != null
                && response.getBody().amount == order.getTotalAmount()
                && response.getBody().getTransactionReference().contains(order.getId())
                && "complete".equals(response.getBody().getStatus().toLowerCase());
    }

    @Override
    public boolean makePaymentToShop(OzowPaymentData paymentData) throws Exception {
        return false;
    }

    @Override
    public boolean makePaymentToShop(StoreProfile store, Order order, double basketAmountExclFees) throws Exception {
       // return ukheshePaymentProvider.makePaymentToShop(order, basketAmountExclFees);
        return false;
    }

    @Override
    public void makePayments(List<Order> ordersList) {

    }

    @Override
    public void makePaymentToMessenger(Order order, double amount) throws Exception {
        ukheshePaymentProvider.makePaymentToMessenger(order, amount);
    }
}
