package io.curiousoft.ijudi.ordermanagement.service.yoco;

public class YocoPayRequest {

    private String token;
    private double amountInCents;
    private String currency;

    public YocoPayRequest(String token, double amountInCents, String currency) {
        this.token = token;
        this.amountInCents = amountInCents;
        this.currency = currency;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public double getAmountInCents() {
        return amountInCents;
    }

    public void setAmountInCents(double amountInCents) {
        this.amountInCents = amountInCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
