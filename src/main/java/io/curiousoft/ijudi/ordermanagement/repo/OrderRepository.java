package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.OrderStage;
import io.curiousoft.ijudi.ordermanagement.model.ShippingData;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<List<Order>> findByCustomerId(String customerId);

    List<Order> findByShopId(String id);

    List<Order> findByShopIdAndStageNot(String id, OrderStage stage);

    void deleteByShopPaidAndStageAndModifiedDateBefore(boolean shopPaid, OrderStage stage, Date date);

    List<Order> findByShippingDataMessengerIdAndStageNot(String id, OrderStage customerNotPaid);

    List<Order> findByStage(OrderStage eq);

    List<Order> findByShopPaidAndStageAndModifiedDateBefore(boolean b, OrderStage stage6WithCustomer, Date pastDate);

    List<Order> findByMessengerPaidAndStageAndShippingData_Type(boolean paid, OrderStage stage6WithCustomer, ShippingData.ShippingType delivery);
}
