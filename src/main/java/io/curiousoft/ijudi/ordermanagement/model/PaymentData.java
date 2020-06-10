package io.curiousoft.ijudi.ordermanagement.model;

public class PaymentData {

    private String fromAccountId;
    private String toAccountId;
    private double amount;
    private String description;

    public PaymentData() {
    }

    public PaymentData(String fromAccount, String toAccount, double amount, String description) {
        this.fromAccountId = fromAccount;
        this.toAccountId = toAccount;
        this.amount = amount;
        this.description = description;
    }

    public String getFromAccountId() {
        return fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public double getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public void setFromAccountId(String fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public void setToAccountId(String toAccountId) {
        this.toAccountId = toAccountId;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
