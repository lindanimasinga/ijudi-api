package io.curiousoft.ijudi.ordermanagement.service.ukheshe;

import io.curiousoft.ijudi.ordermanagement.model.PaymentData;

import java.text.SimpleDateFormat;
import java.util.Date;

public class UkheshePaymentData extends PaymentData {

    private String uniqueId;
    private String externalId;
    private String date;
    private String type = "MANUAL_APP";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    public UkheshePaymentData() {
    }

    public UkheshePaymentData(String fromAccount,
                              String toAccount,
                              double amount,
                              String reference,
                              String uniqueId,
                              String externalId) {
        super(fromAccount, toAccount, amount, reference);
        this.uniqueId = uniqueId;
        this.externalId = externalId;
        this.date = dateFormat.format(new Date());
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getType() {
        return type;
    }

    public String getDate() {
        return date;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setType(String type) {
        this.type = type;
    }

    public SimpleDateFormat getDateFormat() {
        return dateFormat;
    }
}
