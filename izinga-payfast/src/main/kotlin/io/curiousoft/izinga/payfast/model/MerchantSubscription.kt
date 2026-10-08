package io.curiousoft.izinga.payfast.model

import com.fasterxml.jackson.annotation.JsonIgnore
import io.curiousoft.izinga.commons.model.SubscriptionTier
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.util.Date

/**
 * TIER-BILLING-01 / REQ-03: MongoDB document representing a merchant's PayFast subscription.
 *
 * Collection: merchant_subscriptions
 *
 * payFastToken is a billing secret enabling future recurring charges — it MUST NOT appear
 * in any log output. See SEC-TB01-03 and RISK-06.
 *
 * No delete operation is exposed; subscriptions are soft-cancelled via [status] and
 * [cancelledDate] fields. Hard delete is not permitted.
 */
@Document(collection = "merchant_subscriptions")
data class MerchantSubscription(

    /** iZinga-generated payment ID, doubles as PayFast m_payment_id. */
    @Id
    val id: String,

    /** FK to StoreProfile._id. Indexed for fast lookups by storeId. */
    @Indexed
    val storeId: String,

    /** UID of the store owner at time of subscription creation. */
    val ownerId: String,

    /** PREMIUM_1 or PREMIUM_2 — never FREE. */
    val tier: SubscriptionTier,

    /** Current lifecycle status. */
    var status: MerchantSubscriptionStatus,

    /** Subscription amount in Rands (800 for PREMIUM_1, 3000 for PREMIUM_2). */
    val amountRands: BigDecimal,

    /** Server timestamp when this document was first created. */
    val createdDate: Date,

    /** Timestamp when ITN confirmed first payment. Null until ACTIVE. */
    var activatedDate: Date? = null,

    /** Next billing date from PayFast ITN. Null until ACTIVE. */
    var nextBillingDate: Date? = null,

    /** Updated on each successful ITN. Null until first payment. */
    var lastBillingDate: Date? = null,

    /**
     * PayFast subscription token — enables future recurring charges.
     * SEC-TB01-03-B / RISK-06: MUST NOT appear in logs or API responses.
     * SEC-TB01-03-C: @JsonIgnore prevents serialization in any future GET endpoint response.
     *   Jackson annotations do not affect MongoDB persistence — the field is still
     *   stored and retrieved from the database for internal billing use.
     */
    @JsonIgnore
    var payFastToken: String? = null,

    /** PayFast pf_payment_id from ITN. Indexed for replay-prevention lookups. */
    @Indexed
    var payFastPaymentId: String? = null,

    /** Null unless cancelled. */
    var cancelledDate: Date? = null,

    /** Null unless cancelled. */
    var cancelledReason: String? = null
)
