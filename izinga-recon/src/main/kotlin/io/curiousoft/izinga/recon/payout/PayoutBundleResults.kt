package io.curiousoft.izinga.recon.payout

import java.util.*

data class PayoutBundleResults(var bundleId: String = UUID.randomUUID().toString(), var payoutItemResults: List<PayoutItemResults>)

data class PayoutItemResults(var toId: String, var paid: Boolean, var message: String?=null, val type: PayoutType)
