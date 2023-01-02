package io.curiousoft.izinga.recon.payout

import io.curiousoft.izinga.commons.model.BaseModel
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Date

class PayoutBundle(var type: PayoutType, var payouts: List<Payout>, var createdBy: String, var executed: Boolean = false): BaseModel() {

    @org.springframework.data.annotation.Transient
    val payoutTotalAmount: BigDecimal = payouts.sumOf { it.total }
    @org.springframework.data.annotation.Transient
    val numberOfPayouts: Int = payouts.size
}

enum class PayoutType {
    SHOP, MESSENGER
}
