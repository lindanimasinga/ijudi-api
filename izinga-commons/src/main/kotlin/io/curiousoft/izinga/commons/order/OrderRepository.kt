package io.curiousoft.izinga.commons.order

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.ShippingData
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.LocalDate
import java.util.*
import javax.validation.constraints.NotNull

interface OrderRepository : MongoRepository<Order?, String?> {
    fun findByCustomerId(customerId: String?): Optional<List<Order?>?>?
    fun findByShopId(id: String?): List<Order?>?
    fun findByShopIdAndStageNot(id: String?, stage: OrderStage?): List<Order?>?
    fun deleteByShopPaidAndStageAndModifiedDateBefore(shopPaid: Boolean, stage: OrderStage?, date: Date?)
    fun findByShippingDataMessengerIdAndStageNot(id: String?, customerNotPaid: OrderStage?): List<Order?>?
    fun findByStage(eq: OrderStage?): List<Order?>?
    fun findByShopPaidAndStageAndModifiedDateBefore(
        b: Boolean,
        stage6WithCustomer: OrderStage?,
        pastDate: Date?
    ): List<Order?>?

    fun findByMessengerPaidAndStageAndShippingData_Type(
        paid: Boolean,
        stage6WithCustomer: OrderStage?,
        delivery: ShippingData.ShippingType?
    ): List<Order?>?

    fun findByShopPaidAndStage(paid: Boolean, stage7AllPaid: @NotNull OrderStage?): List<Order>?
    fun findByShopPaidAndStageAndPayoutCreated(paid: Boolean, stage7AllPaid: @NotNull OrderStage, payoutCreated: Boolean): List<Order>?
    fun findByMessengerPaidAndStage(paid: Boolean, stage7AllPaid: @NotNull OrderStage?): List<Order>?
    fun findByIdIn(orderIds: List<String>): List<Order>
    fun findByCustomerIdAndShippingDataMessengerIdAndStageIn(customerId: String, messengerId: String, stages: Array<OrderStage>): List<Order>?
    fun findByCustomerIdAndStage(id: String, stage7AllPaid: OrderStage): List<Order>?
    fun findByCustomerIdAndModifiedDateAfter(customerId: String, toDate: Date): List<Order>
    fun findAllByCreatedDateAfter(ninetyDaysAgo: LocalDate): List<Order>
    fun findByShopPaidAndStageAndPayoutCreatedAndCreatedDateAfter(paid: Boolean, stage7AllPaid: @NotNull OrderStage, payoutCreated: Boolean, date: Date): List<Order>
}