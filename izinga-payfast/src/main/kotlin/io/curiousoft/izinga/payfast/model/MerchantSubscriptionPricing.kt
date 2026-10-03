package io.curiousoft.izinga.payfast.model

import java.math.BigDecimal

/**
 * TIER-BILLING-01 / REQ-10: Subscription pricing constants.
 * Any change to pricing requires updating these constants and a changelog entry.
 * Amounts are in Rands, VAT-exclusive, matching ONB-02 published pricing.
 */
object MerchantSubscriptionPricing {
    val PREMIUM_1_RANDS: BigDecimal = BigDecimal("800")
    val PREMIUM_2_RANDS: BigDecimal = BigDecimal("3000")
}
