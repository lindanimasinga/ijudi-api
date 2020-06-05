package io.curiousoft.ijudi.ordermanagent.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.ijudi.ordermanagent.IjudiApplication;
import io.curiousoft.ijudi.ordermanagent.model.BusinessHours;
import io.curiousoft.ijudi.ordermanagent.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagent.model.UserProfile;
import org.junit.Assert;
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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StoreControlerTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    public void create() throws JsonProcessingException, URISyntaxException {
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile user = new StoreProfile("name",
                "myaddress",
                "path to image",
                "9111111707",
                "customer",
                businessHours);

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/user")).body(user), String.class);


        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        StoreProfile responseUser  = new Gson().fromJson(result.getBody(),StoreProfile.class);
        Assert.assertEquals("9111111707", responseUser.getMobileNumber());
        Assert.assertEquals("customer", responseUser.getRole());
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