package io.curiousoft.ijudi.ordermanagement.service.payfast;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

public class PayFastPaymentProviderTest {

    @Test
    @Ignore
    public void generateSignature() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("merchant-id", "10000100");
        headers.add("version", "v1");
        String passphrase = "passphrase";

        String hash = PayFastPaymentProvider.generateSignature(headers, passphrase);
        Assert.assertEquals("", hash);
    }
}