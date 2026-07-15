package io.curiousoft.izinga.recon.payout.repo

import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.ReferralPartnerPayout
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface ReferralPartnerPayoutRepository : MongoRepository<ReferralPartnerPayout, String> {

    /** Dedup check — returns existing payout for the same commission type + trigger. */
    fun findByCommissionTypeAndTriggerReferenceId(
        commissionType: ReferralCommissionType,
        triggerReferenceId: String
    ): ReferralPartnerPayout?

    fun findAllByToIdAndPayoutStage(toId: String, payoutStage: PayoutStage): List<ReferralPartnerPayout>

    fun findByPayoutStage(payoutStage: PayoutStage = PayoutStage.PENDING): List<ReferralPartnerPayout>

    fun findByModifiedDateBetweenAndToId(fromDate: Date, toDate: Date, toId: String): List<ReferralPartnerPayout>

    fun findByModifiedDateBetween(fromDate: Date, toDate: Date): List<ReferralPartnerPayout>

    /**
     * RP-009 fallback reconciliation: find commission records whose triggerReferenceId
     * matches a commission type but has no linked payout yet — used by the reconciliation scan.
     */
    fun findByCommissionType(commissionType: ReferralCommissionType): List<ReferralPartnerPayout>
}
