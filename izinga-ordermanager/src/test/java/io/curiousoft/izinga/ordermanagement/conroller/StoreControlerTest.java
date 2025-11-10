package io.curiousoft.izinga.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.izinga.ordermanagement.IjudiApplication;
import io.curiousoft.izinga.commons.model.Bank;
import io.curiousoft.izinga.commons.model.BusinessHours;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.StoreType;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StoreControlerTest {

    @Autowired
    private TestRestTemplate rest;

    @Ignore
    @Test
    public void create() throws JsonProcessingException, URISyntaxException {
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile store = new StoreProfile(
                StoreType.FOOD,
                "name",
                "shortname",
                "myaddress",
                "path to image",
                "9111111707",
                tags,
                businessHours,
                "ffd4c856-644f-4453-a5ed-84689801a747",
                new Bank());

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/store")).body(store), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        StoreProfile responseUser  = new Gson().fromJson(result.getBody(),StoreProfile.class);
        Assert.assertEquals("9111111707", responseUser.getMobileNumber());
        Assert.assertEquals("store", responseUser.getRole());
        Assert.assertEquals("myaddress", responseUser.getAddress());
        Assert.assertEquals("name", responseUser.getName());
        Assert.assertNotNull(responseUser.getId());
    }

    @Test
    public void update() throws JsonProcessingException, URISyntaxException {
        // First create a store
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile store = new StoreProfile(
                StoreType.FOOD,
                "name",
                "shortname",
                "myaddress",
                "path to image",
                "9111111707",
                tags,
                businessHours,
                "ffd4c856-644f-4453-a5ed-84689801a747",
                new Bank());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/store")).body(store), String.class);
        StoreProfile createdStore = new Gson().fromJson(createResult.getBody(), StoreProfile.class);

        // Update the store
        createdStore.setName("updated name");
        createdStore.setAddress("updated address");

        ResponseEntity<String> updateResult = this.rest.exchange(
                RequestEntity.patch(new URI("/store/" + createdStore.getId())).body(createdStore), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(updateResult.getBody()));
        Assert.assertTrue(updateResult.getStatusCode().is2xxSuccessful());

        StoreProfile updatedStore = new Gson().fromJson(updateResult.getBody(), StoreProfile.class);
        Assert.assertEquals("updated name", updatedStore.getName());
        Assert.assertEquals("updated address", updatedStore.getAddress());
    }

    @Test
    public void findStock() throws JsonProcessingException, URISyntaxException {
        // First create a store with stock
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile store = new StoreProfile(
                StoreType.FOOD,
                "name",
                "shortname",
                "myaddress",
                "path to image",
                "9111111707",
                tags,
                businessHours,
                "ffd4c856-644f-4453-a5ed-84689801a747",
                new Bank());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/store")).body(store), String.class);
        StoreProfile createdStore = new Gson().fromJson(createResult.getBody(), StoreProfile.class);

        // Find stock for the store
        ResponseEntity<String> stockResult = this.rest.exchange(
                RequestEntity.get(new URI("/store/" + createdStore.getId() + "/stock")).build(), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(stockResult.getBody()));
        Assert.assertTrue(stockResult.getStatusCode().is2xxSuccessful());
    }
}