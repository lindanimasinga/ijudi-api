package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Order;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

public interface OrderService {

    Order startOrder(Order order) throws Exception;

    Order finishOder(Order order) throws Exception;

    Order findOrder(String orderId);

    Order progressNextStage(String orderId) throws Exception;

    List<Order> findOrderByUserId(String userId);

    List<Order> findOrderByPhone(String userId) throws Exception;

    List<Order> findOrderByStoreId(String shopId) throws Exception;

    List<Order> findOrderByMessengerId(String id);

    List<Order> findAll();

    Order cancelOrder(String id) throws Exception;
}
