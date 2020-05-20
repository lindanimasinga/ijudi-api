package io.curiousoft.ijudi.ordermanagent.model;

import java.util.ArrayList;
import java.util.List;

public class Basket {


    private String id;
    private List<BasketItem> items = new ArrayList<>();



    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<BasketItem> getItems() {
        return items;
    }

    public void setItems(List<BasketItem> items) {
        this.items = items;
    }
}
