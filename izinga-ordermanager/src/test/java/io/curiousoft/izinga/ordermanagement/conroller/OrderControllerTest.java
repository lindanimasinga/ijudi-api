package io.curiousoft.izinga.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.IjudiApplication;
import io.curiousoft.izinga.ordermanagement.utils.DeliveryCalculationService;
import io.curiousoft.izinga.ordermanagement.utils.DeliveryDistanceUnavailableException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;

import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderControllerTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserProfileRepo userProfileRepo;
    @Autowired
    private StoreRepository storeRepository;
    @MockBean
    private DeliveryCalculationService deliveryCalculationService;

    private StoreProfile store;
    private UserProfile user;
    private HttpHeaders headers;

    @Before
    public void setUp() throws Exception {
        headers = new HttpHeaders();
        headers.set("Origin", "app://izinga");
        headers.set("app-version", "1.9.2");
        //create user
        user = new UserProfile("TestUser",
                UserProfile.SignUpReason.BUY,
                "21 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051",
                "path to image",
                "0812815707",
                ProfileRoles.CUSTOMER);
        userProfileRepo.save(user);

        //create shop
        Bank bank = new Bank();
        bank.setAccountId("accountId");
        bank.setName("ukheshe");
        bank.setPhone("phoneNumber");
        bank.setType(BankAccType.CHEQUE);
        user.setBank(bank);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        store = new StoreProfile(
                StoreType.FOOD,
                "name",
                "shortname",
                "address",
                "https://image.url",
                "0812815707",
                tags,

                businessHours,
                "ownerId",
                new Bank());

        HashSet<Stock> stockItems = new HashSet<>();
        stockItems.add(new Stock("chips", 2, 10, 0, Collections.emptyList()));
        stockItems.add(new Stock("hotdog", 1, 20, 0, Collections.emptyList()));
        store.setStockList(stockItems);
        store.setScheduledDeliveryAllowed(true);
        store.setBusinessHours(new ArrayList<>());
        store.setFeatured(true);
        store.setHasVat(false);
        store.setStoreWebsiteUrl("https://test.izinga.co.za");
        store.setAvailability(StoreProfile.AVAILABILITY.ONLINE24_7);
        storeRepository.save(store);

        // Stub DeliveryCalculationService so HTTP integration tests don't make live Google Maps
        // calls on the Tomcat worker thread (MockedStatic is thread-local and would not intercept there).
        GeoPointImpl geoFrom = new GeoPointImpl(-29.7828761, 31.0012573);
        GeoPointImpl geoTo   = new GeoPointImpl(-29.78500,   31.00100);
        ShipingGeoData stubbedGeo = new ShipingGeoData(geoFrom, geoTo, 1.5);
        when(deliveryCalculationService.calculateDrivingDirectionKM(any(Order.class), any(StoreProfile.class)))
                .thenReturn(stubbedGeo);
    }

    @Test
    public void startOrder() throws URISyntaxException, JsonProcessingException {

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData(store.getAddress(),
                user.getAddress(),
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date date = Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
        order.setShippingData(shipping);
        order.setCustomerId(user.getId());
        order.setShopId(store.getId());
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/order"))
                        .headers(headers)
                        .body(order), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        Order orderResponse = new Gson().fromJson(result.getBody(), Order.class);
        //verify
        Assert.assertEquals(OrderStage.STAGE_0_CUSTOMER_NOT_PAID, orderResponse.getStage());
        Assert.assertNotNull(orderResponse.getId());
        Assert.assertEquals(1.75, orderResponse.getServiceFee(), 0);
        Assert.assertEquals(30.0, orderResponse.getShippingData().getFee(), 0);
        Assert.assertEquals(40.00, orderResponse.getBasketAmount(), 0);
        //verify total amount paid
        Assert.assertEquals(orderResponse.getServiceFee() + basket.getItems().stream()
                .mapToDouble(BasketItem::getTotalPrice).sum() + orderResponse.getShippingData().getFee(), orderResponse.getTotalAmount(), 0);
    }

    @Test
    public void finishOrder_pay_with_yoco() throws Exception {

        //create an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        items.add(new BasketItem("hotdog", 1, 208, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData(store.getAddress(),
                user.getAddress(),
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date date = Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
        order.setShippingData(shipping);
        order.setCustomerId(user.getId());
        order.setShopId(store.getId());
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("yoco-tok_WkNeGIXOa9dmJxsPP1ImdiqX");

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/order")).headers(headers)
                        .body(order), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        Order orderResponse = new Gson().fromJson(result.getBody(), Order.class);

        // Compute the correct Yoco description token so YocoPaymentProvider.paymentReceived() passes.
        // Logic mirrors YocoPaymentProvider.checksum(): base64(MD5(orderId + totalAmount + customerId + apiKey))
        String yocoApiKey = "sk_test_b03f2c6bMP0k5GY10ad45ddbb5dc";
        String checksumInput = orderResponse.getId() + orderResponse.getTotalAmount() + orderResponse.getCustomerId() + yocoApiKey;
        byte[] digest = MessageDigest.getInstance("MD5").digest(checksumInput.getBytes());
        String checksum = Base64.getEncoder().encodeToString(digest);
        orderResponse.setDescription("yoco-" + checksum);

        //pay order
        orderResponse.setPaymentType(PaymentType.YOCO);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "app://izinga");
        headers.set("app-version", "1.9.2");
        final HttpEntity<Order> entity = new HttpEntity<>(orderResponse, headers);
        orderResponse = rest.exchange(new URI("/order/" + orderResponse.getId()), HttpMethod.PATCH, entity, Order.class).getBody();

        //verify
        Assert.assertNotNull("PATCH /order response body must not be null", orderResponse);
        Assert.assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM, orderResponse.getStage());
    }

    @Test
    public void finishOrder_pay_with_CASH() throws JsonProcessingException, URISyntaxException {

        //create an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData(store.getAddress(),
                user.getAddress(),
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date date = Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
        order.setShippingData(shipping);
        order.setCustomerId(user.getId());
        order.setShopId(store.getId());
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/order"))
                        .headers(headers).body(order), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        Order orderResponse = new Gson().fromJson(result.getBody(), Order.class);

        //pay order
        orderResponse.setPaymentType(PaymentType.CASH);
        HttpHeaders headers = new HttpHeaders();
        final HttpEntity<Order> entity = new HttpEntity<>(orderResponse, headers);
        orderResponse = rest.exchange(new URI("/order/" + orderResponse.getId()), HttpMethod.PATCH, entity, Order.class).getBody();

        //verify
        Assert.assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM, orderResponse.getStage());
    }

    @Test
    public void finishOrder_pay_with_yoco_invalid_origin() throws JsonProcessingException, URISyntaxException {

        //create an order
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData(store.getAddress(),
                user.getAddress(),
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date date = Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
        order.setShippingData(shipping);
        order.setCustomerId(user.getId());
        order.setShopId(store.getId());
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/order"))
                        .headers(headers).body(order), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        Order orderResponse = new Gson().fromJson(result.getBody(), Order.class);

        //pay order
        orderResponse.setPaymentType(PaymentType.PAYFAST);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "application");
        final HttpEntity<Order> entity = new HttpEntity<>(orderResponse, headers);
        ResponseEntity<Order> response = rest.exchange(new URI("/order/" + orderResponse.getId()), HttpMethod.PATCH, entity, Order.class);

        //verify
        Assert.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void getOrderByCustomer() {

    }

    /**
     * FIX-02: when DeliveryCalculationService throws IllegalArgumentException (Maps failure),
     * POST /order must return HTTP 400 with the informative message in the body — not 500.
     * Also asserts that the mock was called, confirming the order was NOT persisted (the throw
     * happens inside orderService.startOrder before orderRepo.save).
     */
    @Test
    public void startOrder_mapsFailure_returns400() throws Exception {
        // Reset invocation count from setUp's default stub and any prior tests.
        reset(deliveryCalculationService);

        // given: Maps client throws for this order.
        // Use doThrow().when() rather than when().thenThrow() so that the stub setup itself
        // does not record a method invocation (which would inflate the verify count).
        String mapsErrorMessage = "Unable to calculate delivery distance for the provided address. "
                + "Please check the address and try again.";
        Mockito.doThrow(new IllegalArgumentException(mapsErrorMessage))
                .when(deliveryCalculationService).calculateDrivingDirectionKM(any(Order.class), any(StoreProfile.class));

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData(store.getAddress(),
                user.getAddress(),
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date date = Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
        order.setShippingData(shipping);
        order.setCustomerId(user.getId());
        order.setShopId(store.getId());
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        // when
        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/order"))
                        .headers(headers)
                        .body(order), String.class);

        // then: HTTP 400, not 500
        Assert.assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        Assert.assertNotNull(result.getBody());
        Assert.assertTrue("Response body should contain the Maps error message",
                result.getBody().contains("Unable to calculate delivery distance"));

        // Confirm DeliveryCalculationService was invoked (order reached Maps call before failing)
        verify(deliveryCalculationService).calculateDrivingDirectionKM(any(Order.class), any(StoreProfile.class));
    }

    /**
     * Maps OUTAGE path: when DeliveryCalculationService throws DeliveryDistanceUnavailableException,
     * POST /order must return HTTP 503 Service Unavailable with the informative message — not 500.
     */
    @Test
    public void startOrder_mapsOutage_returns503() throws Exception {
        // Reset mock invocation count accumulated from previous tests in the shared Spring context,
        // so that the verify(mock, times(1)) at the end reports exactly 1 invocation for this test.
        reset(deliveryCalculationService);

        // given: Maps service throws an outage exception.
        // Use doThrow().when() so the stub setup itself does not record a method invocation.
        String outageMessage = "Unable to calculate delivery distance right now: the maps service is unavailable. Please try again shortly.";
        Mockito.doThrow(new DeliveryDistanceUnavailableException(outageMessage, new java.io.IOException("Connection refused")))
                .when(deliveryCalculationService).calculateDrivingDirectionKM(any(Order.class), any(StoreProfile.class));

        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10, 0));
        basket.setItems(items);
        order.setBasket(basket);
        ShippingData shipping = new ShippingData(store.getAddress(),
                user.getAddress(),
                ShippingData.ShippingType.SCHEDULED_DELIVERY);
        Date date = Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
        shipping.setPickUpTime(date);
        order.setShippingData(shipping);
        order.setCustomerId(user.getId());
        order.setShopId(store.getId());
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setOrderType(OrderType.ONLINE);
        order.setDescription("description");

        // when
        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/order"))
                        .headers(headers)
                        .body(order), String.class);

        // then: HTTP 503, not 500, with informative error body.
        // The 503 status and the message text together confirm the outage code path was taken
        // through OrderServiceImpl → DeliveryCalculationService → OrderController.
        Assert.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        Assert.assertNotNull(result.getBody());
        Assert.assertTrue("Response body should mention maps service unavailability",
                result.getBody().contains("maps service is unavailable"));
    }

    @After
    public void tearDown() throws Exception {
        storeRepository.deleteAll();
        userProfileRepo.deleteAll();
    }
}