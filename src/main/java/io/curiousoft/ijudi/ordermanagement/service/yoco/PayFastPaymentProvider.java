package io.curiousoft.ijudi.ordermanagement.service.yoco;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.service.PaymentProvider;
import io.curiousoft.ijudi.ordermanagement.service.ukheshe.UkheshePaymentProvider;
import io.curiousoft.ijudi.ordermanagement.utils.IjudiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Logger;

@Service
public class PayFastPaymentProvider extends PaymentProvider<YocoPaymentData> {

    private static Logger logger = Logger.getLogger(UkheshePaymentProvider.class.getName());
    private final String apiKey;
    private final String baseUrl;
    private final UkheshePaymentProvider ukheshePaymentProvider;


    @Autowired
    public PayFastPaymentProvider(@Value("${ozow.api.url}") String baseUrl,
                                  @Value("${ozow.api.key}") String apiKey,
                                  UkheshePaymentProvider ukheshePaymentProvider) {
        super(PaymentType.PAYFAST);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.ukheshePaymentProvider = ukheshePaymentProvider;
    }

    @Override
    protected boolean paymentReceived(Order order) throws Exception {
/*        //get transactions
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
        ResponseEntity<YocoPaymentData> response = rest.exchange(url, HttpMethod.GET, entity, YocoPaymentData.class);
        return response.getBody() != null;
           //     && response.getBody().amount == order.getTotalAmount()
           //     && response.getBody().getTransactionReference().contains(order.getId())
           //     && "complete".equals(response.getBody().getStatus().toLowerCase());*/
        return true;
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

    public static String generateSignature(HttpHeaders headers, String passphrase) throws NoSuchAlgorithmException,
            UnsupportedEncodingException {
        StringBuilder valuesToHash = new StringBuilder();
        headers.entrySet()
                .stream().sorted((item, item2) -> item.getKey().compareTo(item2.getKey()))
                .forEach(header -> valuesToHash.append(header.getKey())
                        .append("=")
                        .append(header.getValue().get(0))
                        .append("&"));
        valuesToHash.append("passphrase=").append(URLEncoder.encode(passphrase, "UTF-8"));
        return IjudiUtils.generateMD5Hash(valuesToHash.toString());
    }
}
