package io.curiousoft.izinga.ordermanagement.service.paymentverify.yoco;

public class YocoPayRequest {

    private String token;
    private int amountInCents;
    private String currency;

    public YocoPayRequest(String token, int amountInCents, String currency) {
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

    public int getAmountInCents() {
        return amountInCents;
    }

    public void setAmountInCents(int amountInCents) {
        this.amountInCents = amountInCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}