package io.curiousoft.izinga.recon.payout.repo

import io.curiousoft.izinga.recon.payout.AmbassadorPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface AmbassadorPayoutRepository : MongoRepository<AmbassadorPayout, String> {
    fun findByToId(toId: String): List<AmbassadorPayout>
    fun findAllByToIdAndPayoutStage(toId: String, payoutStage: PayoutStage): List<AmbassadorPayout>
    fun findByToIdAndPayoutStage(toId: String, payoutStage: PayoutStage): AmbassadorPayout?
    fun findByPayoutStage(payoutStage: PayoutStage = PayoutStage.PENDING): List<AmbassadorPayout>
    fun findByCreatedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<AmbassadorPayout>
    fun findByCreatedDateBetween(fromDate: Date, toDate: Date): List<AmbassadorPayout>
    fun findByModifiedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<AmbassadorPayout>
    fun findByModifiedDateBetween(fromDate: Date, toDate: Date): List<AmbassadorPayout>
}
