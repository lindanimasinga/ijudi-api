package io.curiousoft.izinga.commons.order

import io.curiousoft.izinga.commons.model.Order

interface OrderService {
    fun startOrder(order: Order?): Order?

    fun finishOder(order: Order?): Order?

    fun findOrder(orderId: String?): Order?

    fun progressNextStage(orderId: String?): Order?

    fun applyPromoCode(promoCode: String?, order: Order?): Order?

    fun findOrderByUserId(userId: String?): MutableList<Order?>?

    fun findOrderByPhone(userId: String?): MutableList<Order?>?

    fun findOrderByStoreId(shopId: String?): MutableList<Order?>?

    fun findOrderByMessengerId(id: String?): MutableList<Order?>?

    fun findAll(): MutableList<Order?>?

    fun cancelOrder(id: String?): Order?
}
