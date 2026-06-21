package io.curiousoft.izinga.ordermanagement.conroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.curiousoft.izinga.commons.model.Promotion;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.repo.PromotionRepository;
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
import java.util.Date;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PromotionsControllerTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    PromotionRepository promotionRepository;

    @Test
    public void create() throws URISyntaxException, JsonProcessingException {
        Promotion promotion = new Promotion();
        promotion.setTitle("Test Promotion");
        promotion.setDescription("Test Description");
        promotion.setImageUrl("https://test.com/image.png");
        promotion.setStoreType(StoreType.FOOD);
        promotion.setStartDate(new Date());
        promotion.setEndDate(new Date());

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/promotion")).body(promotion), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        Promotion responsePromotion = new Gson().fromJson(result.getBody(), Promotion.class);
        Assert.assertEquals("Test Promotion", responsePromotion.getTitle());
        Assert.assertEquals("Test Description", responsePromotion.getDescription());
        Assert.assertEquals(StoreType.FOOD, responsePromotion.getStoreType());
        Assert.assertNotNull(responsePromotion.getId());
    }

    @Test
    public void update() throws URISyntaxException, JsonProcessingException {
        // First create a promotion
        Promotion promotion = new Promotion();
        promotion.setTitle("Original Title");
        promotion.setDescription("Original Description");
        promotion.setImageUrl("https://test.com/image.png");
        promotion.setStoreType(StoreType.FOOD);
        promotion.setStartDate(new Date());
        promotion.setEndDate(new Date());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/promotion")).body(promotion), String.class);
        Promotion createdPromotion = new Gson().fromJson(createResult.getBody(), Promotion.class);

        // Update the promotion
        createdPromotion.setTitle("Updated Title");
        createdPromotion.setDescription("Updated Description");

        ResponseEntity<String> updateResult = this.rest.exchange(
                RequestEntity.patch(new URI("/promotion/" + createdPromotion.getId())).body(createdPromotion), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(updateResult.getBody()));
        Assert.assertTrue(updateResult.getStatusCode().is2xxSuccessful());

        Promotion updatedPromotion = new Gson().fromJson(updateResult.getBody(), Promotion.class);
        Assert.assertEquals("Updated Title", updatedPromotion.getTitle());
        Assert.assertEquals("Updated Description", updatedPromotion.getDescription());
    }

    @Test
    public void findPromotion() throws URISyntaxException, JsonProcessingException {
        // First create a promotion
        Promotion promotion = new Promotion();
        promotion.setTitle("Find Me");
        promotion.setDescription("Test Description");
        promotion.setImageUrl("https://test.com/image.png");
        promotion.setStoreType(StoreType.FOOD);
        promotion.setStartDate(new Date());
        promotion.setEndDate(new Date());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/promotion")).body(promotion), String.class);
        Promotion createdPromotion = new Gson().fromJson(createResult.getBody(), Promotion.class);

        // Find the promotion by ID
        ResponseEntity<String> findResult = this.rest.exchange(
                RequestEntity.get(new URI("/promotion/" + createdPromotion.getId())).build(), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(findResult.getBody()));
        Assert.assertTrue(findResult.getStatusCode().is2xxSuccessful());

        Promotion foundPromotion = new Gson().fromJson(findResult.getBody(), Promotion.class);
        Assert.assertEquals(createdPromotion.getId(), foundPromotion.getId());
        Assert.assertEquals("Find Me", foundPromotion.getTitle());
    }

    @Test
    public void deletePromotion() throws URISyntaxException, JsonProcessingException {
        // First create a promotion
        Promotion promotion = new Promotion();
        promotion.setTitle("Delete Me");
        promotion.setDescription("Test Description");
        promotion.setImageUrl("https://test.com/image.png");
        promotion.setStoreType(StoreType.FOOD);
        promotion.setStartDate(new Date());
        promotion.setEndDate(new Date());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/promotion")).body(promotion), String.class);
        Promotion createdPromotion = new Gson().fromJson(createResult.getBody(), Promotion.class);

        // Delete the promotion
        ResponseEntity<String> deleteResult = this.rest.exchange(
                RequestEntity.delete(new URI("/promotion/" + createdPromotion.getId())).build(), String.class);

        Assert.assertTrue(deleteResult.getStatusCode().is2xxSuccessful());

        // Verify it's deleted by trying to find it
        ResponseEntity<String> findResult = this.rest.exchange(
                RequestEntity.get(new URI("/promotion/" + createdPromotion.getId())).build(), String.class);

        Assert.assertTrue(findResult.getStatusCode().is4xxClientError());
    }

    @Test
    public void findAllPromotions() throws URISyntaxException, JsonProcessingException {
        // First create a promotion
        Promotion promotion = new Promotion();
        promotion.setTitle("List Me");
        promotion.setDescription("Test Description");
        promotion.setImageUrl("https://test.com/image.png");
        promotion.setStoreType(StoreType.FOOD);
        promotion.setStartDate(new Date());
        promotion.setEndDate(new Date());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/promotion")).body(promotion), String.class);

        // Find all promotions by store type
        ResponseEntity<String> findResult = this.rest.exchange(
                RequestEntity.get(new URI("/promotion?storeType=FOOD")).build(), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(findResult.getBody()));
        Assert.assertTrue(findResult.getStatusCode().is2xxSuccessful());

        // Parse the response as an array of promotions
        Promotion[] promotions = new Gson().fromJson(findResult.getBody(), Promotion[].class);
        Assert.assertTrue(promotions.length > 0);
    }

    @After
    public void tearDown() throws Exception {
        promotionRepository.deleteAll();
    }
}
