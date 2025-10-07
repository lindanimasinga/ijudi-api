package io.curiousoft.izinga.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.ordermanagement.IjudiApplication;
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
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RootControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    public void health() throws URISyntaxException, JsonProcessingException {
        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.get(new URI("/")).build(), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        Map response = new ObjectMapper().readValue(result.getBody(), Map.class);
        Assert.assertEquals("ok", response.get("status"));
    }
}
