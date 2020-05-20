package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Order;

public interface OrderService {

    Order startOrder(Order order) throws Exception;

    Order finishOder(Order order) throws Exception;

    Order findOrder(String orderId);
}
