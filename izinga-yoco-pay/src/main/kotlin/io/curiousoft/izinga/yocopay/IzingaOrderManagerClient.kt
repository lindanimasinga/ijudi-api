package io.curiousoft.izinga.yocopay

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.order.OrderService
import org.springframework.stereotype.Service

@Service
class IzingaOrderManagerClient(private val orderService: OrderService) {

    fun findOrder(orderId: String): Order? {
        return orderService.findOrder(orderId)
    }

    fun finishOrder(orderId: String, order: Order): Order? {
        return orderService.finishOder(order)
    }

    fun cancelOrder(orderId: String) {
        orderService.cancelOrder(orderId)
    }
}
