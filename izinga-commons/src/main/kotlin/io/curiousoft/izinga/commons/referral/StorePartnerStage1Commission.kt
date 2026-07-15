package io.curiousoft.izinga.commons.referral

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

/**
 * RP-007: Stage 1 commission created when a referred food store's profile is approved
 * (profileApproved transitions to true for a STORE with referredByPartnerId set).
 *
 * Deduplication is enforced at the database level via a unique index on [storeId].
 * A DuplicateKeyException on insert signals the commission was already created.
 *
 * Commission amount: R100.00 flat.
 * Status starts as PENDING — payout wiring is out of scope until RP-009 is resolved.
 * RP-008 (Stage 2) requires this record to exist before it triggers.
 */
@Document(collection = "store_partner_stage1_commissions")
data class StorePartnerStage1Commission(
    @Id val id: String = UUID.randomUUID().toString(),

    /**
     * Unique per store — MongoDB enforces uniqueness so only one Stage 1 commission
     * is ever created per referred store.
     */
    @Indexed(unique = true)
    val storeId: String,

    /** The REFERRAL_PARTNER user ID who will receive this commission. */
    val referralPartnerId: String,

    val amount: BigDecimal = BigDecimal("100.00"),

    val status: ReferralCommissionStatus = ReferralCommissionStatus.PENDING,

    val createdAt: Date = Date()
)
