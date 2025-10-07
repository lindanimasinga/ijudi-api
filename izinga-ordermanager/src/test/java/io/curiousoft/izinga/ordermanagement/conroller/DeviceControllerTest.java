package io.curiousoft.izinga.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.ordermanagement.IjudiApplication;
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
public class DeviceControllerTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    DeviceRepository deviceRepository;

    @Test
    public void create() throws URISyntaxException, JsonProcessingException {
        Device device = new Device();
        device.setToken("test-token-123");
        device.setUserId("user123");
        device.setPlatform("android");

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/device")).body(device), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        Device responseDevice = new Gson().fromJson(result.getBody(), Device.class);
        Assert.assertEquals("test-token-123", responseDevice.getToken());
        Assert.assertEquals("user123", responseDevice.getUserId());
        Assert.assertEquals("android", responseDevice.getPlatform());
        Assert.assertNotNull(responseDevice.getId());
    }

    @Test
    public void update() throws URISyntaxException, JsonProcessingException {
        // First create a device
        Device device = new Device();
        device.setToken("test-token-456");
        device.setUserId("user456");
        device.setPlatform("ios");

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/device")).body(device), String.class);
        Device createdDevice = new Gson().fromJson(createResult.getBody(), Device.class);

        // Update the device
        createdDevice.setToken("updated-token");
        createdDevice.setPlatform("android");

        ResponseEntity<String> updateResult = this.rest.exchange(
                RequestEntity.patch(new URI("/device/" + createdDevice.getId())).body(createdDevice), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(updateResult.getBody()));
        Assert.assertTrue(updateResult.getStatusCode().is2xxSuccessful());

        Device updatedDevice = new Gson().fromJson(updateResult.getBody(), Device.class);
        Assert.assertEquals("updated-token", updatedDevice.getToken());
        Assert.assertEquals("android", updatedDevice.getPlatform());
    }

    @Test
    public void findDevice() throws URISyntaxException, JsonProcessingException {
        // First create a device
        Device device = new Device();
        device.setToken("test-token-789");
        device.setUserId("user789");
        device.setPlatform("web");

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/device")).body(device), String.class);
        Device createdDevice = new Gson().fromJson(createResult.getBody(), Device.class);

        // Find the device by ID
        ResponseEntity<String> findResult = this.rest.exchange(
                RequestEntity.get(new URI("/device/" + createdDevice.getId())).build(), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(findResult.getBody()));
        Assert.assertTrue(findResult.getStatusCode().is2xxSuccessful());

        Device foundDevice = new Gson().fromJson(findResult.getBody(), Device.class);
        Assert.assertEquals(createdDevice.getId(), foundDevice.getId());
        Assert.assertEquals("test-token-789", foundDevice.getToken());
        Assert.assertEquals("user789", foundDevice.getUserId());
    }

    @Test
    public void deleteDevice() throws URISyntaxException, JsonProcessingException {
        // First create a device
        Device device = new Device();
        device.setToken("test-token-delete");
        device.setUserId("userDelete");
        device.setPlatform("android");

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/device")).body(device), String.class);
        Device createdDevice = new Gson().fromJson(createResult.getBody(), Device.class);

        // Delete the device
        ResponseEntity<String> deleteResult = this.rest.exchange(
                RequestEntity.delete(new URI("/device/" + createdDevice.getId())).build(), String.class);

        Assert.assertTrue(deleteResult.getStatusCode().is2xxSuccessful());

        // Verify it's deleted by trying to find it
        ResponseEntity<String> findResult = this.rest.exchange(
                RequestEntity.get(new URI("/device/" + createdDevice.getId())).build(), String.class);

        Assert.assertTrue(findResult.getStatusCode().is4xxClientError());
    }

    @After
    public void tearDown() throws Exception {
        deviceRepository.deleteAll();
    }
}
