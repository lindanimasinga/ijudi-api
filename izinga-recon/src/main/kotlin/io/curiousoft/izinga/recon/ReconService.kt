package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent
import io.curiousoft.izinga.recon.payout.*
import java.util.*

interface ReconService {

    fun generatePayoutForShopAndOrder(order: Order): ShopPayout?

    fun generatePayoutForMessengerAndOrder(order: Order): MessengerPayout?

    fun generatePayoutForAmbassadorAndApproval(driver: UserProfile, ambassador: UserProfile): AmbassadorPayout?

    fun updatePayoutStatus(bundleResponse: PayoutBundleResults)

    fun getAllPayoutBundles(payoutType: PayoutType, from: Date, toDate: Date): List<Payout>

    fun getCurrentPayoutBundleForShops(): PayoutBundle

    fun getCurrentPayoutBundleForMessenger(): PayoutBundle

    fun getAllPayouts(payoutType: PayoutType, from: Date, toDate: Date, toId: String): List<Payout>

    fun getAllPayoutsForMessengerAdmin(from: Date, toDate: Date, messengerAdminId: String, messengerId: String? = null): List<Payout>

    fun findPayout(bundleId: String, payoutId: String): Payout?
    fun updateBundle(bundle: PayoutBundle)

    fun handleProfileUpdated(event: ProfileUpdatedEvent)
}