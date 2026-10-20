package io.curiousoft.izinga.commons.order

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.ShippingData
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import java.time.LocalDate
import java.util.*
import jakarta.validation.constraints.NotNull

interface OrderRepository : MongoRepository<Order?, String?> {
    fun findByCustomerId(customerId: String?): Optional<List<Order?>?>?
    fun findByShopId(id: String?): List<Order?>?
    fun findByShopIdAndStageNot(id: String?, stage: OrderStage?): List<Order?>?
    fun findAllByShopPaidAndStageAndModifiedDateBefore(shopPaid: Boolean, stage: OrderStage?, date: Date?): List<Order?>?
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
    fun findByShippingDataMessengerId(id: String): List<Order>
    fun findByShippingDataMessengerIdIn(ids: List<String>): List<Order>
    fun findByShippingDataMessengerIdInAndStageNot(ids: List<String>, customerNotPaid: OrderStage): List<Order>
    fun findByStageNotIn(stages: List<OrderStage>): List<Order>

    /**
     * ADR-020 / start_order idempotency: looks up a recent order for the given customer
     * that carries the specified idempotencyKey in its tag map. The createdDate window
     * filter is applied inside the query itself via the {@code \$gte} clause.
     * Note: {@code BaseModel.createdDate} is annotated {@code @JsonProperty("date")} for
     * JSON serialisation only — the actual BSON field stored by Spring Data MongoDB is
     * {@code createdDate} (no {@code @Field} override), so the query must use that name.
     * Sparse index on (customerId, tag.idempotencyKey) is declared on Order via @CompoundIndex.
     */
    @Query("{ 'customerId': ?0, 'tag.idempotencyKey': ?1, 'createdDate': { '\$gte': ?2 } }")
    fun findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow(
        customerId: String,
        idempotencyKey: String,
        afterDate: Date
    ): Order?
}