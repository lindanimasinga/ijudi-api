package io.curiousoft.ijudi.ordermanagement.service.ukheshe;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;
import io.curiousoft.ijudi.ordermanagement.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;

@Service
public class UkheshePaymentService extends PaymentService {

    private static Logger logger = Logger.getLogger(UkheshePaymentService.class.getName());

    private final String username;
    private final String password;
    private final String baseUrl;
    private final String customerId;
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private UkhesheAuthtoken ukhesheAuthtoken;


    public UkheshePaymentService(@Value("${ukheshe.apiUrl}") String baseUrl,
                                 @Value("${ukheshe.username}") String username,
                                 @Value("${ukheshe.customerId}") String customerId,
                                 @Value("${ukheshe.password}") String password) {
        super(PaymentType.UKHESHE);
        this.username = username;
        this.password = password;
        this.baseUrl = baseUrl;
        this.customerId = customerId;
    }

    @Override
    public boolean paymentReceived(Order order) throws Exception {

        if(ukhesheAuthtoken == null || ukhesheAuthtoken.hasExpired()) {
            RestTemplate rest = new RestTemplate();
            URI uri = new URI(baseUrl + "/authentication/login");
            Map<String, String> credentials = new HashMap<>();
            credentials.put("identity", username);
            credentials.put("password", password);
            HttpEntity<Map> request = new HttpEntity<>(credentials);
            ResponseEntity<UkhesheAuthtoken> response = rest.postForEntity(uri, request, UkhesheAuthtoken.class);
            if (response.getStatusCodeValue() == 200) {
                ukhesheAuthtoken = response.getBody();
            } else {
                throw new Exception("There was a problem with your ukheshe account. unable to authenticate");
            }
        }

        //get transactions
        String url = baseUrl + "/customers/" + customerId + "/transactions";
        url = url + "?dateFromIncl=" + dateFormat.format(order.getDate()) ;
        url = url + "&dateToExcl=" + dateFormat.format(new Date());
        URI uri = new URI(url);
        RestTemplate rest = new RestTemplateBuilder().build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", ukhesheAuthtoken.getHeaderValue());
        //Create a new HttpEntity
        final HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<UkhesheTransaction[]> response = rest.exchange(url, HttpMethod.GET, entity, UkhesheTransaction[].class);
        if(Objects.requireNonNull(response.getBody()).length > 0) {
            logger.info(response.getBody()[0].getDescription());
        }
        return Stream.of(Objects.requireNonNull(response.getBody()))
                .filter(ukhesheTransaction -> isSameOrder(order, ukhesheTransaction))
                .count() > 0;
    }

    private boolean isSameOrder(Order order, UkhesheTransaction ukhesheTransaction) {

        return order.getDescription().equals(ukhesheTransaction.getDescription())
                            && order.getTotalAmount() == ukhesheTransaction.getAmount();
    }


    public UkhesheAuthtoken getUkhesheAuthtoken() {
        return ukhesheAuthtoken;
    }

    public void setUkhesheAuthtoken(UkhesheAuthtoken ukhesheAuthtoken) {
        this.ukhesheAuthtoken = ukhesheAuthtoken;
    }

    private static class UkhesheAuthtoken {

        private Date expires;
        private String headerValue;

        public Date getExpires() {
            return expires;
        }

        public void setExpires(Date expires) {
            this.expires = expires;
        }

        public String getHeaderValue() {
            return headerValue;
        }

        public void setHeaderValue(String headerValue) {
            this.headerValue = headerValue;
        }

        public boolean hasExpired() {
            return expires.before(new Date());
        }
    }
}
