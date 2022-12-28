package io.curiousoft.izinga.ordermanagement.service.yoco;

public class YocoPaymentResponse {
    private String status;
    private String id;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}