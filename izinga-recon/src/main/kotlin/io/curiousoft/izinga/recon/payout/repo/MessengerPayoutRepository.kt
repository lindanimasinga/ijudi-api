package io.curiousoft.izinga.recon.payout.repo

import io.curiousoft.izinga.recon.payout.MessengerPayout
import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutStage
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface MessengerPayoutRepository : MongoRepository<MessengerPayout, String> {
    fun findByToId(toId: String): List<MessengerPayout>
    fun findByToIdAndPayoutStage(shopId: String, payoutStage: PayoutStage): MessengerPayout?
    fun findByPayoutStage(payoutStage: PayoutStage = PayoutStage.PENDING): List<MessengerPayout>
    fun findByCreatedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<MessengerPayout>
}