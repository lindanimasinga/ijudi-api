package io.curiousoft.izinga.ordermanagement.notification;

import io.curiousoft.izinga.commons.model.BasketItem;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;

import java.util.ArrayList;
import java.util.List;

class Data{
    public String account_name;
    public StoreProfile store;
    public UserProfile customer;
    public Order order;
    public List<BasketItem> items;
}

class From{
    public String email;
}

class Personalization {
    public String email;
    public Data data;

    public Personalization(String email, Data data) {
        this.email = email;
        this.data = data;
    }
}

public class EmailRequest{
    public From from;
    public ArrayList<To> to;
    public ArrayList<Personalization> personalization;
    public String template_id;
}

class To {
    public String email;
    public To(String email) {
        this.email = email;
    }
}
