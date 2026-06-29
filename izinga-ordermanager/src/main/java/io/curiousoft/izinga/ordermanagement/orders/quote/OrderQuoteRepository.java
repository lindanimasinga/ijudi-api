package io.curiousoft.izinga.ordermanagement.orders.quote;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderQuoteRepository extends MongoRepository<OrderQuote, String> {

    List<OrderQuote> findBySentToMessengerIds(String id);
}
