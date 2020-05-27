package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Order;

import java.util.List;

public interface OrderService {

    Order startOrder(Order order) throws Exception;

    Order finishOder(Order order) throws Exception;

    Order findOrder(String orderId);

    List<Order> findOrderByUserId(String userId);

    List<Order> findOrderByPhone(String userId) throws Exception;
}
