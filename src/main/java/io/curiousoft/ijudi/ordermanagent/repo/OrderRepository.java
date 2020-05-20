package io.curiousoft.ijudi.ordermanagent.repo;

import io.curiousoft.ijudi.ordermanagent.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {
}
