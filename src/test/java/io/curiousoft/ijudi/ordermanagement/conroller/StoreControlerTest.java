package io.curiousoft.ijudi.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.ijudi.ordermanagement.IjudiApplication;
import io.curiousoft.ijudi.ordermanagement.model.BusinessHours;
import io.curiousoft.ijudi.ordermanagement.model.ProfileRoles;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
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
import java.util.ArrayList;
import java.util.Collections;
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
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile store = new StoreProfile("name",
                "myaddress",
                "path to image",
                "9111111707",
                tags,
                ProfileRoles.STORE,
                businessHours,
                "ffd4c856-644f-4453-a5ed-84689801a747");

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
    public void update() {
    }

    @Test
    public void findStock() {
    }
}