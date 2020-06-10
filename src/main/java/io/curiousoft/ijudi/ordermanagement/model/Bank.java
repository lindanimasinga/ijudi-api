package io.curiousoft.ijudi.ordermanagement.model;

import javax.validation.constraints.NotBlank;

public class Bank {

    @NotBlank(message = "Bank name not valid")
    private String name;
    @NotBlank(message = "Bank phone not valid")
    private String phone;
    @NotBlank(message = "Bank account id not valid")
    private String accountId;
    private String type;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
