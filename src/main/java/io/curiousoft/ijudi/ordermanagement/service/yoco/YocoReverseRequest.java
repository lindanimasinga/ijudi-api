package io.curiousoft.ijudi.ordermanagement.service.yoco;

public class YocoReverseRequest {

    private String chargeId;

    public YocoReverseRequest(String chargeId) {
        this.chargeId = chargeId;
    }

    public String getChargeId() {
        return chargeId;
    }

    public void setChargeId(String chargeId) {
        this.chargeId = chargeId;
    }
}
