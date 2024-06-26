package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutBundleResults
import io.curiousoft.izinga.recon.payout.PayoutType
import java.util.*

interface ReconService {

    fun generateNextPayoutsToShop(): PayoutBundle?

    fun generateNextPayoutsToMessenger(): PayoutBundle?

    fun updatePayoutStatus(bundleResponse: PayoutBundleResults): PayoutBundle?

    fun getAllPayoutBundles(payoutType: PayoutType, from: Date, toDate: Date): List<PayoutBundle>

    fun getAllPayouts(payoutType: PayoutType, from: Date, toDate: Date, toId: String): List<Payout>

    fun findPayout(bundleId: String, payoutId: String): Payout?
    fun updateBundle(bundle: PayoutBundle): PayoutBundle
}