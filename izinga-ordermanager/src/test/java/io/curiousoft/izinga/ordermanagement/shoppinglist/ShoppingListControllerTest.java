package io.curiousoft.izinga.ordermanagement.shoppinglist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
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
import java.util.ArrayList;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ShoppingListControllerTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    ShoppingListRepository shoppingListRepository;

    @Test
    public void createShoppingList() throws URISyntaxException, JsonProcessingException {
        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setUserId("user123");
        shoppingList.setName("My Shopping List");
        List<ShoppingItem> items = new ArrayList<>();
        items.add(new ShoppingItem("Milk", 1, false));
        items.add(new ShoppingItem("Bread", 2, false));
        shoppingList.setItems(items);

        ResponseEntity<String> result = this.rest.exchange(
                RequestEntity.post(new URI("/shopping-list")).body(shoppingList), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(result.getBody()));
        Assert.assertTrue(result.getStatusCode().is2xxSuccessful());

        ShoppingList responseList = new Gson().fromJson(result.getBody(), ShoppingList.class);
        Assert.assertEquals("user123", responseList.getUserId());
        Assert.assertEquals("My Shopping List", responseList.getName());
        Assert.assertNotNull(responseList.getId());
    }

    @Test
    public void updateShoppingList() throws URISyntaxException, JsonProcessingException {
        // First create a shopping list
        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setUserId("user456");
        shoppingList.setName("Original List");
        shoppingList.setItems(new ArrayList<>());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/shopping-list")).body(shoppingList), String.class);
        ShoppingList createdList = new Gson().fromJson(createResult.getBody(), ShoppingList.class);

        // Update the shopping list
        createdList.setName("Updated List");

        ResponseEntity<String> updateResult = this.rest.exchange(
                RequestEntity.put(new URI("/shopping-list/" + createdList.getId())).body(createdList), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(updateResult.getBody()));
        Assert.assertTrue(updateResult.getStatusCode().is2xxSuccessful());

        ShoppingList updatedList = new Gson().fromJson(updateResult.getBody(), ShoppingList.class);
        Assert.assertEquals("Updated List", updatedList.getName());
    }

    @Test
    public void getShoppingListById() throws URISyntaxException, JsonProcessingException {
        // First create a shopping list
        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setUserId("user789");
        shoppingList.setName("Test List");
        shoppingList.setItems(new ArrayList<>());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/shopping-list")).body(shoppingList), String.class);
        ShoppingList createdList = new Gson().fromJson(createResult.getBody(), ShoppingList.class);

        // Get the shopping list by ID
        ResponseEntity<String> getResult = this.rest.exchange(
                RequestEntity.get(new URI("/shopping-list/" + createdList.getId())).build(), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(getResult.getBody()));
        Assert.assertTrue(getResult.getStatusCode().is2xxSuccessful());

        ShoppingList foundList = new Gson().fromJson(getResult.getBody(), ShoppingList.class);
        Assert.assertEquals(createdList.getId(), foundList.getId());
        Assert.assertEquals("Test List", foundList.getName());
    }

    @Test
    public void getAllShoppingLists() throws URISyntaxException, JsonProcessingException {
        // First create a shopping list
        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setUserId("userListAll");
        shoppingList.setName("List for All");
        shoppingList.setItems(new ArrayList<>());

        this.rest.exchange(
                RequestEntity.post(new URI("/shopping-list")).body(shoppingList), String.class);

        // Get all shopping lists for the user
        ResponseEntity<String> getResult = this.rest.exchange(
                RequestEntity.get(new URI("/shopping-list?userId=userListAll")).build(), String.class);

        System.out.println(new ObjectMapper().writeValueAsString(getResult.getBody()));
        Assert.assertTrue(getResult.getStatusCode().is2xxSuccessful());

        ShoppingList[] lists = new Gson().fromJson(getResult.getBody(), ShoppingList[].class);
        Assert.assertTrue(lists.length > 0);
    }

    @Test
    public void deleteShoppingList() throws URISyntaxException, JsonProcessingException {
        // First create a shopping list
        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setUserId("userDelete");
        shoppingList.setName("Delete Me");
        shoppingList.setItems(new ArrayList<>());

        ResponseEntity<String> createResult = this.rest.exchange(
                RequestEntity.post(new URI("/shopping-list")).body(shoppingList), String.class);
        ShoppingList createdList = new Gson().fromJson(createResult.getBody(), ShoppingList.class);

        // Delete the shopping list
        ResponseEntity<String> deleteResult = this.rest.exchange(
                RequestEntity.delete(new URI("/shopping-list/" + createdList.getId())).build(), String.class);

        Assert.assertTrue(deleteResult.getStatusCode().is2xxSuccessful());
    }

    @After
    public void tearDown() throws Exception {
        shoppingListRepository.deleteAll();
    }
}
