package io.curiousoft.izinga.recon.payout.repo

import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.ShopPayout
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import java.util.*

interface ShopPayoutRepository : MongoRepository<ShopPayout, String> {
    fun findByToId(toId: String): List<ShopPayout>
    fun findAllByToIdAndPayoutStage(toId: String, payoutStage: PayoutStage): List<ShopPayout>
    fun findByToIdAndPayoutStage(shopId: String, payoutStage: PayoutStage): ShopPayout?
    fun findByPaid(paid: Boolean = false): List<ShopPayout>
    fun findByPayoutStage(payoutStage: PayoutStage = PayoutStage.PENDING): List<ShopPayout>
    fun findByCreatedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<ShopPayout>
    fun findByCreatedDateBetween(fromDate: Date, toDate: Date): List<ShopPayout>
    fun findByModifiedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<ShopPayout>
    fun findByModifiedDateBetween(fromDate: Date, toDate: Date): List<ShopPayout>

    /**
     * ADR-028 cross-stage idempotency guard: returns true if ANY ShopPayout (of any PayoutStage)
     * for [toId] already contains an embedded order whose id equals [orderId].
     *
     * Field path `orders._id` is used because Spring Data MongoDB maps the `@Id`-annotated `id`
     * field in embedded subdocuments to `_id`. QA should verify this against real sandbox data
     * (look at a saved ShopPayout document and confirm the embedded order field name).
     */
    @Query(value = "{ 'toId': ?0, 'orders._id': ?1 }", exists = true)
    fun existsByToIdAndOrderId(toId: String, orderId: String): Boolean
}
