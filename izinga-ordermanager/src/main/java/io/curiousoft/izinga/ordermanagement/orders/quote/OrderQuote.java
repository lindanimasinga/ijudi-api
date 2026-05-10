package io.curiousoft.izinga.ordermanagement.orders.quote;

import io.curiousoft.izinga.commons.model.BaseModel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
@Getter
@Setter
public class OrderQuote extends BaseModel {
    private final String orderId;
    private final String storeId;
    private String accpetedByMessengerId;
    private List<String> sentToMessengerIds;

    public OrderQuote(String orderId, String storeId) {
        this.orderId = orderId;
        this.storeId = storeId;
    }
}
