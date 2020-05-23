package io.curiousoft.ijudi.ordermanagent.repo;

import io.curiousoft.ijudi.ordermanagent.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<List<Order>> findByCustomerId(String customerId);
}
