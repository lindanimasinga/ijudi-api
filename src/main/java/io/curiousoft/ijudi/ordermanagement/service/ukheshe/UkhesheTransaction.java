package io.curiousoft.ijudi.ordermanagement.service.ukheshe;

import java.util.Date;

public class UkhesheTransaction {

    private Date date;
    private double amount;
    private String description;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
