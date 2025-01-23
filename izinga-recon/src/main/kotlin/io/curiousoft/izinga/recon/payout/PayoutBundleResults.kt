package io.curiousoft.izinga.recon.payout

data class PayoutBundleResults(var bundleId: String, var payoutItemResults: List<PayoutItemResults>)

data class PayoutItemResults(var toId: String, var paid: Boolean, var message: String?=null, val payoutType: PayoutType)
