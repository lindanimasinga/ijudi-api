package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.recon.payout.*
import java.math.BigDecimal
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

    /**
     * RP-009: Creates a ReferralPartnerPayout for the given partner, amount, and commission type.
     * Performs a dedup check via (commissionType, triggerReferenceId). Returns null (with WARN log)
     * if a payout already exists for the same trigger, or if the partner has no bank details set.
     *
     * @param partnerId          The REFERRAL_PARTNER UserProfile id.
     * @param amount             The commission amount (e.g. 15.00, 100.00, 150.00).
     * @param commissionType     Discriminates RP-006 / RP-007 / RP-008.
     * @param triggerReferenceId customerId (RP-006) or storeId (RP-007/RP-008) — unique per commission type.
     */
    fun generatePayoutForReferralPartner(
        partnerId: String,
        amount: BigDecimal,
        commissionType: ReferralCommissionType,
        triggerReferenceId: String
    ): ReferralPartnerPayout?

    /**
     * RP-009 fallback reconciliation: scans PENDING commission records from all three
     * collections that do not yet have a linked ReferralPartnerPayout and creates payouts
     * for them. Partners without bank details are skipped (WARN log) — they will be picked
     * up on the next reconciliation pass once bank details are set.
     *
     * Safe to call repeatedly — the dedup index prevents duplicates even if called concurrently.
     */
    fun reconcilePendingReferralCommissions()
}