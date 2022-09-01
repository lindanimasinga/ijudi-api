package io.curiousoft.ijudi.ordermanagement.notification;

import io.curiousoft.ijudi.ordermanagement.model.*;

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

