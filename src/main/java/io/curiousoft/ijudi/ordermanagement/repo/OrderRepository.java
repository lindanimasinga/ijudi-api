package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.OrderStage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<List<Order>> findByCustomerId(String customerId);

    List<Order> findByShopPaidAndStageAndDateBefore(boolean shopPaid, OrderStage stage, Date orderDate);

    List<Order> findByShopId(String id);

    List<Order> findByShopIdAndStageNot(String id, OrderStage stage);

    void deleteByShopPaidAndStageAndDateBefore(boolean shopPaid, OrderStage stage, Date date);
}
