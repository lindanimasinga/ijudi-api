package io.curiousoft.izinga.ordermanagement.cucumber;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.curiousoft.izinga.commons.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class OrderSteps {

    private Order order;
    private double calculatedTotal;
    private double calculatedBasketTotal;

    @Given("a customer places an order with the following basket items")
    public void aCustomerPlacesAnOrderWithTheFollowingBasketItems(List<Map<String, String>> items) {
        order = new Order();
        Basket basket = new Basket();
        List<BasketItem> basketItems = new ArrayList<>();
        for (Map<String, String> item : items) {
            basketItems.add(new BasketItem(
                    item.get("name"),
                    Integer.parseInt(item.get("quantity")),
                    Double.parseDouble(item.get("price")),
                    0));
        }
        basket.setItems(basketItems);
        order.setBasket(basket);
        order.setCustomerId("customerId");
        order.setShopId("shopId");
        order.setDescription("test order");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }

    @And("the order has a delivery fee of {double}")
    public void theOrderHasADeliveryFeeOf(double fee) {
        ShippingData shippingData = new ShippingData("shopAddress", "toAddress", ShippingData.ShippingType.DELIVERY);
        shippingData.setFee(fee);
        order.setShippingData(shippingData);
    }

    @And("the order has a service fee of {double}")
    public void theOrderHasAServiceFeeOf(double serviceFee) {
        order.setServiceFee(serviceFee);
    }

    @And("the order has free delivery")
    public void theOrderHasFreeDelivery() {
        order.setFreeDelivery(true);
    }

    @When("the total amount is calculated")
    public void theTotalAmountIsCalculated() {
        calculatedTotal = order.getTotalAmount();
        calculatedBasketTotal = order.getBasketAmount();
    }

    @Then("the total amount should be {double}")
    public void theTotalAmountShouldBe(double expectedTotal) {
        assertEquals(expectedTotal, calculatedTotal, 0.01);
    }

    @And("the basket amount should be {double}")
    public void theBasketAmountShouldBe(double expectedBasketTotal) {
        assertEquals(expectedBasketTotal, calculatedBasketTotal, 0.01);
    }

    @Given("an order in stage {word}")
    public void anOrderInStage(String stage) {
        order = new Order();
        order.setStage(OrderStage.valueOf(stage));
        order.setCustomerId("customerId");
        order.setShopId("shopId");
        order.setDescription("test order");
        order.setOrderType(OrderType.ONLINE);
        Basket basket = new Basket();
        basket.setItems(new ArrayList<>());
        order.setBasket(basket);
    }

    @When("the order stage is updated to {word}")
    public void theOrderStageIsUpdatedTo(String newStage) {
        order.setStage(OrderStage.valueOf(newStage));
    }

    @Then("the order stage should be {word}")
    public void theOrderStageShouldBe(String expectedStage) {
        assertEquals(OrderStage.valueOf(expectedStage), order.getStage());
    }
}
