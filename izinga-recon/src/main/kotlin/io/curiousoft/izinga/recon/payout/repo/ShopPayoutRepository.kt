package io.curiousoft.izinga.recon.payout.repo

import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.ShopPayout
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface ShopPayoutRepository : MongoRepository<ShopPayout, String> {
    fun findByToId(toId: String): List<ShopPayout>
    fun findByToIdAndPayoutStage(shopId: String, payoutStage: PayoutStage): ShopPayout?
    fun findByPaid(paid: Boolean = false): List<ShopPayout>
    fun findByPayoutStage(payoutStage: PayoutStage = PayoutStage.PENDING): List<ShopPayout>
    fun findByCreatedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<ShopPayout>
    fun findByCreatedDateBetween(fromDate: Date, toDate: Date): List<ShopPayout>
}