package io.curiousoft.izinga.commons.referral

/**
 * Lifecycle status for all referral commission records.
 *
 * PENDING  — record created, not yet included in a payout run.
 * PAID     — included in a completed payout run (wired by RP-009).
 * CANCELLED — commission voided (e.g. store deactivated before payout).
 */
enum class ReferralCommissionStatus {
    PENDING,
    PAID,
    CANCELLED
}
