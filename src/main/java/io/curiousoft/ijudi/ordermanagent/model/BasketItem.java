package io.curiousoft.ijudi.ordermanagent.model;

public class BasketItem {

    private String name;
    private int quantity;
    private double price;
    private double discountPerc;

    public BasketItem(String name, int quantity, double price, double discountPerc) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.discountPerc = discountPerc;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getDiscountPerc() {
        return discountPerc;
    }

    public void setDiscountPerc(double discountPerc) {
        this.discountPerc = discountPerc;
    }
}
