package io.curiousoft.izinga.ordermanagement.service.yoco;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.Order;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static java.lang.String.format;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class YocoPaymentProviderTest {

    YocoPaymentProvider sut = new YocoPaymentProvider("https://url.com", "apikey", new ObjectMapper());

    @Test
    public void get_charge_id_from_description() throws Exception {
        //give
        Order order = mock(Order.class);
        when(order.getId()).thenReturn("12345");
        when(order.getTotalAmount()).thenReturn(100.00);
        var checksum = sut.checksum(format("%s%s%s", order.getId(),order.getTotalAmount(), order.getCustomerId()));
        when(order.getDescription()).thenReturn(format("sdfasdfsadfasdf:yoco-%s:asdfasdf876786342", checksum));

        //when
        var received = sut.paymentReceived(order);

        //then
        Assertions.assertTrue(received);
    }
}