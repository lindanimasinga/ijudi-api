package io.curiousoft.ijudi.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.ijudi.ordermanagement.IjudiApplication;
import io.curiousoft.ijudi.ordermanagement.model.ProfileRoles;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.junit.After;
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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserControllerTest {


    @Autowired
    private TestRestTemplate rest;
    @Autowired
    UserProfileRepo userProfileRepo;

    @Test
    public void create() throws URISyntaxException, JsonProcessingException {

        UserProfile user = new UserProfile("name",
                "myaddress",
                "path to image",
                "9111111707",
                ProfileRoles.CUSTOMER);

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/user")).body(user), String.class);


        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        UserProfile responseUser  = new Gson().fromJson(result.getBody(),UserProfile.class);
        Assert.assertEquals("9111111707", responseUser.getMobileNumber());
        Assert.assertEquals(ProfileRoles.CUSTOMER, responseUser.getRole());
        Assert.assertEquals("myaddress", responseUser.getAddress());
        Assert.assertEquals("name", responseUser.getName());
        Assert.assertNotNull(responseUser.getId());
    }

    @Test
    public void update() {
    }

    @Test
    public void findUser() {
    }

    @After
    public void tearDown() throws Exception {
        userProfileRepo.deleteAll();
    }
}