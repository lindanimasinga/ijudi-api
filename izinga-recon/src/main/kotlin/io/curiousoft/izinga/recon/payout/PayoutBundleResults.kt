package io.curiousoft.izinga.recon.payout

data class PayoutBundleResults(var bundleId: String, var payoutItemResults: List<PayoutItemResults>? = null)

data class PayoutItemResults(var payoutId: String, var paid: Boolean, var message: String?=null)
