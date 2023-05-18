package io.curiousoft.izinga.messaging.domain;

import java.util.Date;

public class ZoomSMSMessage {

    private String recipientNumber;
    private Date dateToSend;
    private String dataField;
    private String campaign;
    private String message;

    public ZoomSMSMessage(String recipientNumber, String message) {
        this.recipientNumber = recipientNumber;
        this.message = message;
    }

    public String getRecipientNumber() {
        return recipientNumber;
    }

    public void setRecipientNumber(String recipientNumber) {
        this.recipientNumber = recipientNumber;
    }

    public Date getDateToSend() {
        return dateToSend;
    }

    public void setDateToSend(Date dateToSend) {
        this.dateToSend = dateToSend;
    }

    public String getDataField() {
        return dataField;
    }

    public void setDataField(String dataField) {
        this.dataField = dataField;
    }

    public String getCampaign() {
        return campaign;
    }

    public void setCampaign(String campaign) {
        this.campaign = campaign;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
