package io.curiousoft.izinga.recon.payout.repo

import io.curiousoft.izinga.recon.payout.MessengerPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import java.util.*

interface MessengerPayoutRepository : MongoRepository<MessengerPayout, String> {
    fun findByToId(toId: String): List<MessengerPayout>
    fun findAllByToIdAndPayoutStage(toId: String, payoutStage: PayoutStage): List<MessengerPayout>
    fun findByToIdAndPayoutStage(shopId: String, payoutStage: PayoutStage): MessengerPayout?
    fun findByPayoutStage(payoutStage: PayoutStage = PayoutStage.PENDING): List<MessengerPayout>
    fun findByCreatedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<MessengerPayout>
    fun findByCreatedDateBetween(fromDate: Date, toDate: Date): List<MessengerPayout>
    fun findByModifiedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<MessengerPayout>
    fun findByModifiedDateBetweenAndToIdIn(fromDate: Date, toDate: Date, toId: List<String>): List<MessengerPayout>
    fun findByModifiedDateBetween(fromDate: Date, toDate: Date): List<MessengerPayout>

    /**
     * ADR-028 cross-stage idempotency guard: returns true if ANY MessengerPayout (of any PayoutStage)
     * for [toId] already contains an embedded order whose id equals [orderId].
     *
     * Field path `orders._id` is used because Spring Data MongoDB maps the `@Id`-annotated `id`
     * field in embedded subdocuments to `_id`. QA should verify this against real sandbox data.
     */
    @Query(value = "{ 'toId': ?0, 'orders._id': ?1 }", exists = true)
    fun existsByToIdAndOrderId(toId: String, orderId: String): Boolean
}
