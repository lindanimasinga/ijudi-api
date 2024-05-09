package io.curiousoft.izinga.ordermanagement.service.paymentverify.yoco;

public class YocoReverseRequest {
    private String checkoutId;

    public YocoReverseRequest(String checkoutId) {
        this.checkoutId = checkoutId;
    }
}