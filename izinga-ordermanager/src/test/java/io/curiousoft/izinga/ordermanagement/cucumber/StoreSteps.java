package io.curiousoft.izinga.ordermanagement.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.curiousoft.izinga.commons.model.*;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class StoreSteps {

    private StoreProfile storeProfile;

    private static BusinessHours openHours(DayOfWeek day) {
        Date open = new Date(System.currentTimeMillis());
        Date close = new Date(System.currentTimeMillis() + 8 * 60 * 60 * 1000L);
        return new BusinessHours(day, open, close);
    }

    @Given("a new store profile is created")
    public void aNewStoreProfileIsCreated() {
        List<BusinessHours> businessHours = new ArrayList<>();
        businessHours.add(openHours(DayOfWeek.MONDAY));
        storeProfile = new StoreProfile(
                StoreType.FOOD,
                "Test Store",
                "test-store",
                "123 Test Street",
                "https://image.example.com/store.png",
                "0812345678",
                new ArrayList<>(Collections.singletonList("Food")),
                businessHours,
                "ownerId",
                new Bank());
    }

    @Then("the store availability should be OFFLINE")
    public void theStoreAvailabilityShouldBeOFFLINE() {
        assertEquals(StoreProfile.AVAILABILITY.OFFLINE, storeProfile.getAvailability());
    }

    @When("business hours are added for TUESDAY")
    public void businessHoursAreAddedForTUESDAY() {
        storeProfile.getBusinessHours().add(openHours(DayOfWeek.TUESDAY));
    }

    @Then("the store should have {int} business hour entries")
    public void theStoreShouldHaveBusinessHourEntries(int count) {
        assertEquals(count, storeProfile.getBusinessHours().size());
    }

    @Then("the store stock list should be empty")
    public void theStoreStockListShouldBeEmpty() {
        assertTrue(storeProfile.getStockList().isEmpty());
    }

    @Given("a store with the tag {string}")
    public void aStoreWithTheTag(String tag) {
        List<BusinessHours> businessHours = new ArrayList<>();
        businessHours.add(openHours(DayOfWeek.MONDAY));
        storeProfile = new StoreProfile(
                StoreType.FOOD,
                "Test Store",
                "test-store-tags",
                "123 Test Street",
                "https://image.example.com/store.png",
                "0812345678",
                new ArrayList<>(Collections.singletonList(tag)),
                businessHours,
                "ownerId",
                new Bank());
    }

    @Then("the store tags should contain {string}")
    public void theStoreTagsShouldContain(String tag) {
        assertNotNull(storeProfile.getTags());
        assertTrue(storeProfile.getTags().contains(tag));
    }
}


