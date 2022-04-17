package io.curiousoft.ijudi.ordermanagement.service.yoco;

import org.junit.Test;

import static org.junit.Assert.*;

public class YocoPaymentProviderTest {

    @Test
    public void get_charge_id_from_description() {
        String descrition = "yoco:1233435_token|charge:12345sdfsdf_charge";

        //when
        String chargeId = descrition.split("\\|")[1].replace("charge-", "");

        //verify
        assertEquals("12345sdfsdf_charge", chargeId);
    }
}