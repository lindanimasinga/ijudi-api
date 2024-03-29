package io.curiousoft.izinga.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.IjudiApplication;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.URISyntaxException;
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
        storeRepository.save(store);
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
    public void finishOrder_pay_with_yoco() throws JsonProcessingException, URISyntaxException {

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

        //pay order
        orderResponse.setPaymentType(PaymentType.YOCO);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "app://izinga");
        headers.set("app-version", "1.9.2");
        final HttpEntity<Order> entity = new HttpEntity<>(orderResponse, headers);
        orderResponse = rest.exchange(new URI("/order/" + orderResponse.getId()), HttpMethod.PATCH, entity, Order.class).getBody();

        //verify
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

    @After
    public void tearDown() throws Exception {
        storeRepository.deleteAll();
        userProfileRepo.deleteAll();
    }
}