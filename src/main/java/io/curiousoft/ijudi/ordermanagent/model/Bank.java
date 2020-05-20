package io.curiousoft.ijudi.ordermanagent.model;

import javax.validation.constraints.NotBlank;

public class Bank {

    @NotBlank(message = "Bank name not valid")
    private String name;
    @NotBlank(message = "Bank phone not valid")
    private String phone;
    @NotBlank(message = "Bank account id not valid")
    private String accountId;
    private int customerId;
    private String type;
    private double currentBalance = 0;
    private double availableBalance = 0;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(double currentBalance) {
        this.currentBalance = currentBalance;
    }

    public double getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(double availableBalance) {
        this.availableBalance = availableBalance;
    }
}
