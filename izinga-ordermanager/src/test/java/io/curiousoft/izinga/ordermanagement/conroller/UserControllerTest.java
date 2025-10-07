package io.curiousoft.izinga.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.IjudiApplication;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
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
                UserProfile.SignUpReason.BUY,
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
    public void update() throws URISyntaxException, JsonProcessingException {
        // First create a user
        UserProfile user = new UserProfile("name",
                UserProfile.SignUpReason.BUY,
                "myaddress",
                "path to image",
                "9111111707",
                ProfileRoles.CUSTOMER);

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/user")).body(user), String.class);
        UserProfile createdUser = new Gson().fromJson(createResult.getBody(), UserProfile.class);

        // Update the user
        createdUser.setName("updated name");
        createdUser.setAddress("updated address");

        ResponseEntity<String> updateResult = this.rest.exchange(
                RequestEntity.patch(new URI("/user/" + createdUser.getId())).body(createdUser), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(updateResult.getBody()));
        Assert.assertTrue(updateResult.getStatusCode().is2xxSuccessful());

        UserProfile updatedUser = new Gson().fromJson(updateResult.getBody(), UserProfile.class);
        Assert.assertEquals("updated name", updatedUser.getName());
        Assert.assertEquals("updated address", updatedUser.getAddress());
        Assert.assertEquals("9111111707", updatedUser.getMobileNumber());
    }

    @Test
    public void findUser() throws URISyntaxException, JsonProcessingException {
        // First create a user
        UserProfile user = new UserProfile("name",
                UserProfile.SignUpReason.BUY,
                "myaddress",
                "path to image",
                "9111111707",
                ProfileRoles.CUSTOMER);

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/user")).body(user), String.class);
        UserProfile createdUser = new Gson().fromJson(createResult.getBody(), UserProfile.class);

        // Find the user by ID
        ResponseEntity<String> findResult = this.rest.exchange(
                RequestEntity.get(new URI("/user/" + createdUser.getId())).build(), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(findResult.getBody()));
        Assert.assertTrue(findResult.getStatusCode().is2xxSuccessful());

        UserProfile foundUser = new Gson().fromJson(findResult.getBody(), UserProfile.class);
        Assert.assertEquals(createdUser.getId(), foundUser.getId());
        Assert.assertEquals("name", foundUser.getName());
        Assert.assertEquals("9111111707", foundUser.getMobileNumber());
    }

    @After
    public void tearDown() throws Exception {
        userProfileRepo.deleteAll();
    }
}