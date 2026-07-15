package io.curiousoft.izinga.commons.referral

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

/**
 * RP-006: Commission record created when a referred food customer places their first
 * completed order (STAGE_7_ALL_PAID).
 *
 * Deduplication is enforced at the database level via a unique index on [customerId].
 * A DuplicateKeyException on insert signals the commission was already created — this
 * is the expected idempotency mechanism. Do NOT rely solely on application-level checks.
 *
 * Commission amount: R15.00 flat.
 * Status starts as PENDING — payout wiring is out of scope until RP-009 is resolved.
 */
@Document(collection = "food_customer_referral_commissions")
data class FoodCustomerReferralCommission(
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

    val amount: BigDecimal = BigDecimal("15.00"),

    val status: ReferralCommissionStatus = ReferralCommissionStatus.PENDING,

    val createdAt: Date = Date()
)
