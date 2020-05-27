package io.curiousoft.ijudi.ordermanagent.service.cash;

import io.curiousoft.ijudi.ordermanagent.model.Order;
import io.curiousoft.ijudi.ordermanagent.model.PaymentType;
import io.curiousoft.ijudi.ordermanagent.service.PaymentService;
import io.curiousoft.ijudi.ordermanagent.service.ukheshe.UkhesheTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class CashPaymentService extends PaymentService {

    public CashPaymentService() {
        super(PaymentType.CASH);
    }

    @Override
    public boolean paymentReceived(Order order) throws Exception {
        return true;
    }
}
