package io.curiousoft.izinga.recon.payout

import io.curiousoft.izinga.commons.model.BaseModel
import java.math.BigDecimal

class PayoutBundle(var type: PayoutType,
                   @org.springframework.data.annotation.Transient var payouts: List<Payout>,
                   var createdBy: String): BaseModel() {

    val payoutTotalAmount: BigDecimal = payouts.sumOf { it.total }
    val numberOfPayouts: Int = payouts.size
}

enum class PayoutType {
    SHOP, MESSENGER
}
