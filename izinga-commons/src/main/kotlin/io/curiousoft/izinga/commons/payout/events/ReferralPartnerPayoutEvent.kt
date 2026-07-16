package io.curiousoft.izinga.commons.payout.events

import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import org.springframework.context.ApplicationEvent
import java.math.BigDecimal

/**
 * RP-009: Published after a ReferralPartnerPayout is persisted. Allows downstream
 * listeners (notifications, analytics) to react without coupling to the recon module.
 */
class ReferralPartnerPayoutEvent(
    source: Any,
    val partnerId: String,
    val commissionType: ReferralCommissionType,
    val triggerReferenceId: String,
    val commissionAmount: BigDecimal,
    val payoutId: String
) : ApplicationEvent(source)
