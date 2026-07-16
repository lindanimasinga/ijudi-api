package io.curiousoft.izinga.commons.referral

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

/**
 * RP-008: Stage 2 commission created when a referred food store's FIRST customer order
 * reaches STAGE_7_ALL_PAID, provided Stage 1 commission already exists for that store.
 *
 * Deduplication is enforced at the database level via a unique index on [storeId].
 * A DuplicateKeyException on insert signals the commission was already created.
 *
 * Commission amount: R150.00 flat.
 * Status starts as PENDING — payout wiring is out of scope until RP-009 is resolved.
 */
@Document(collection = "store_partner_stage2_commissions")
data class StorePartnerStage2Commission(
    @Id val id: String = UUID.randomUUID().toString(),

    /**
     * Unique per store — MongoDB enforces uniqueness so only one Stage 2 commission
     * is ever created per referred store.
     */
    @Indexed(unique = true)
    val storeId: String,

    /** The REFERRAL_PARTNER user ID who will receive this commission. */
    val referralPartnerId: String,

    /** The order that triggered Stage 2 (for audit trail). */
    val triggeringOrderId: String,

    val amount: BigDecimal = BigDecimal("150.00"),

    val status: ReferralCommissionStatus = ReferralCommissionStatus.PENDING,

    val createdAt: Date = Date()
)
