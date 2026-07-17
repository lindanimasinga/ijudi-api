package io.curiousoft.izinga.commons.referral

/**
 * RP-009: Distinguishes which referral programme rule created a ReferralPartnerPayout.
 *
 * FOOD_CUSTOMER_REFERRAL      — RP-006: R15 when a referred food customer completes first order.
 * STORE_PARTNER_STAGE_1       — RP-007: R100 when a referred food store is approved.
 * STORE_PARTNER_STAGE_2       — RP-008: R150 when a referred food store's first order completes.
 * FURNITURE_CUSTOMER_REFERRAL — RP-012: 5% of Total Delivery Charge when a referred furniture
 *                               customer completes their first furniture (MOVERS) delivery order.
 */
enum class ReferralCommissionType {
    FOOD_CUSTOMER_REFERRAL,
    STORE_PARTNER_STAGE_1,
    STORE_PARTNER_STAGE_2,
    FURNITURE_CUSTOMER_REFERRAL
}
