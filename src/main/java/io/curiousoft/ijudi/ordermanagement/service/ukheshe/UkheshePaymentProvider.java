package io.curiousoft.ijudi.ordermanagement.service.ukheshe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.PaymentType;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import io.curiousoft.ijudi.ordermanagement.service.PaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@Service
public class UkheshePaymentProvider extends PaymentProvider<UkheshePaymentData> {

    private static Logger logger = Logger.getLogger(UkheshePaymentProvider.class.getName());

    private final String username;
    private final String password;
    private final String baseUrl;
    private final String customerId;
    private final String mainAccount;
    private final StoreRepository storeRepo;
    private final UserProfileRepo userProfileRepo;

    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private UkhesheAuthtoken ukhesheAuthtoken;
    private ResponseEntity<UkhesheTransaction[]> response;


    public UkheshePaymentProvider(@Value("${ukheshe.apiUrl}") String baseUrl,
                                  @Value("${ukheshe.username}") String username,
                                  @Value("${ukheshe.customerId}") String customerId,
                                  @Value("${ukheshe.password}") String password,
                                  @Value("${ukheshe.main.account}") String mainAccount,
                                  StoreRepository storeRepo,
                                  UserProfileRepo userProfileRepo) {
        super(PaymentType.UKHESHE);
        this.username = username;
        this.password = password;
        this.baseUrl = baseUrl;
        this.customerId = customerId;
        this.mainAccount = mainAccount;
        this.storeRepo = storeRepo;
        this.userProfileRepo = userProfileRepo;
    }

    @Override
    public boolean paymentReceived(Order order) throws Exception {

        if(ukhesheAuthtoken == null || ukhesheAuthtoken.hasExpired()) {
            refreshToken();
        }

        //get transactions
        LocalDateTime fromLocalDate = LocalDateTime.ofInstant(order.getCreatedDate().toInstant(),
                ZoneId.systemDefault()).minusMinutes(10);
        Date fromDate = Date.from(fromLocalDate.atZone(ZoneId.systemDefault()).toInstant());

        String url = baseUrl + "/customers/" + customerId + "/transactions";
        url = url + "?dateFromIncl=" + dateFormat.format(fromDate) ;
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

    private void refreshToken() throws Exception {
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

    @Override
    public boolean makePaymentToShop(UkheshePaymentData paymentData) throws Exception {

        if(ukhesheAuthtoken == null || ukhesheAuthtoken.hasExpired()) {
            refreshToken();
        }

        logger.log(Level.INFO, new ObjectMapper().writeValueAsString(paymentData));
        //get payment or transfer
        String url = baseUrl + "/transfers/";
        URI uri = new URI(url);
        RestTemplate rest = new RestTemplateBuilder()
                .defaultHeader("Authorization", ukhesheAuthtoken.getHeaderValue())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        //Create a new HttpEntity
        final HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<UkheshePaymentData> response = rest.postForEntity(uri,
                paymentData, UkheshePaymentData.class);

        if(response.getStatusCodeValue() != 200) {
            throw new Exception(String.valueOf(response.getBody()));
        }

        return true;
    }

    @Override
    public boolean makePaymentToShop(Order order, double basketAmountExclFees) throws Exception {
        String fromAccount = mainAccount;
        StoreProfile shop = storeRepo.findById(order.getShopId())
                .orElseThrow(() -> new Exception("shop does not exist"));
        String shopAccount = !StringUtils.isEmpty(shop.getBank().getAccountId()) ?
                shop.getBank().getAccountId() : shop.getBank().getPhone();

        UkheshePaymentData payment = new UkheshePaymentData(
                fromAccount,
                shopAccount,
                basketAmountExclFees,
                order.getDescription(),
                order.getId() + "_2",
                order.getId());
        return makePaymentToShop(payment);
    }

    @Override
    public void makePayments(List<Order> ordersList) {

    }

    @Override
    public void makePaymentToMessenger(Order order, double amount) throws Exception {
        UserProfile shop = userProfileRepo.findById(order.getShippingData().getMessengerId())
                .orElseThrow(() -> new Exception("Messenger does not exist"));
        String shopAccount = !StringUtils.isEmpty(shop.getBank().getAccountId()) ?
                shop.getBank().getAccountId() : shop.getBank().getPhone();

        UkheshePaymentData payment = new UkheshePaymentData(
                mainAccount,
                shopAccount,
                amount,
                order.getDescription(),
                order.getId() + "_3",
                order.getId());
        makePaymentToShop(payment);
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
}
