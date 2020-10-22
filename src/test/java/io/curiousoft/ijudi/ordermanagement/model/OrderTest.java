package io.curiousoft.ijudi.ordermanagement.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class OrderTest {

    @Test
    public void getTotalAmount() {
        Order order = new Order();
        Basket basket = new Basket();
        List<BasketItem> items = new ArrayList<>();
        items.add(new BasketItem("chips", 2, 10.111, 0));
        items.add(new BasketItem("hotdog", 1, 20, 0));
        basket.setItems(items);
        order.setBasket(basket);


        ShippingData shipping = new ShippingData("shopAddress",
                "to address",
                ShippingData.ShippingType.DELIVERY);
        shipping.setMessengerId("messagerID");
        shipping.setFee(11.4933);
        shipping.setBuildingType(BuildingType.HOUSE);
        order.setShippingData(shipping);
        order.setDescription("081281445");
        order.setCustomerId("customerId");
        order.setOrderType(OrderType.ONLINE);
        order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
        order.setShopId("shopId");
        order.setDescription("desc");
        order.setServiceFee(1.99);

        double totalAmount = order.getTotalAmount();
        double basketTotalAmount = order.getBasketAmount();
        Assert.assertEquals(53.71, totalAmount, 0);
        Assert.assertEquals(40.22, basketTotalAmount, 0);
    }
}