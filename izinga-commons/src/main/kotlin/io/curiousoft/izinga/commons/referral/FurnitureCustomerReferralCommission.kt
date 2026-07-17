package io.curiousoft.izinga.commons.referral

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

/**
 * RP-012: Commission record created when a referred furniture customer places their first
 * completed furniture (MOVERS store type) delivery order (STAGE_7_ALL_PAID).
 *
 * Commission = 5% of Total Delivery Charge, where:
 *   Total Delivery Charge = shippingData.fee × (1 + serviceFeePerc)
 *
 * The [amount] field stores the computed value at trigger time. It must NOT be recomputed
 * during reconciliation — always use the persisted value.
 *
 * Deduplication is enforced at the database level via a unique index on [customerId].
 * A DuplicateKeyException on insert signals the commission was already created — this
 * is the expected idempotency mechanism. Do NOT rely solely on application-level checks.
 *
 * Schedule 1 Clause 2 of the Referral Partner Agreement governs this commission type.
 */
@Document(collection = "furniture_customer_referral_commissions")
data class FurnitureCustomerReferralCommission(
    @Id val id: String = UUID.randomUUID().toString(),

    /**
     * Unique per customer — MongoDB enforces uniqueness so only one commission is
     * ever created per referred customer, regardless of how many orders they place.
     */
    @Indexed(unique = true)
    val customerId: String,

    /** The REFERRAL_PARTNER user ID who will receive this commission. */
    val referralPartnerId: String,

    /** The order that triggered this commission (for audit trail). */
    val triggeringOrderId: String,

    /**
     * Commission amount computed at trigger time per the formula:
     *   amount = shippingData.fee × (1 + serviceFeePerc) × 0.05
     * Never hardcoded — always passed in from the caller.
     */
    val amount: BigDecimal,

    val status: ReferralCommissionStatus = ReferralCommissionStatus.PENDING,

    val createdAt: Date = Date()
)
