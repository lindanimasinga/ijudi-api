package io.curiousoft.izinga.ordermanagement.shoppinglist;

import lombok.Data;

@Data
public class ShoppingItem {
    private String name;
    private String shopName;
    private String shopId;
    private String imageUrl;
    private int quantity;
    private String priceRage;
}
