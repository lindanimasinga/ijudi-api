package io.curiousoft.izinga.recon.payout

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.BaseModel
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.lang.StringBuilder
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
abstract class Payout(
    var toId: String,
    var bundleId: String?,
    var toName: String,
    var toType: BankAccType,
    var toBankName: String,
    var toAccountNumber: String,
    var toBranchCode: String,
    var fromReference: String,
    var toReference: String,
    var emailNotify: String?,
    var emailAddress: String?,
    var emailSubject: String?,
    var orders: MutableSet<Order>,
    var tips: MutableSet<Tip>? = null,
    var payoutStage: PayoutStage): BaseModel(id = generateId()) {
    abstract var paid: Boolean
    abstract val total: BigDecimal
    var emailSent = false
    /** GAP-3: persisted failure reason from the bank reconciliation result, if available. */
    var failureReason: String? = null
    /** GAP-3: number of times this payout has been submitted and come back with paid=false. */
    var attemptCount: Int = 0
}

/**
 * ADR-028 / GAP-2: bare @Document pins the collection name to the Spring Data MongoDB
 * default (uncapitalised simple class name = "messengerPayout"), which is the name that
 * was already in use before this annotation was added.  The compound index enables the
 * cross-stage double-pay guard query {toId, orders._id} to execute as an index scan
 * rather than a collection scan on every scheduler retry.
 *
 * "orders._id" maps to Order.id because Spring Data MongoDB serialises the @Id-annotated
 * field of an embedded subdocument as "_id" (see BaseModel.kt line 11-12).
 */
@Document
@CompoundIndex(name = "messenger_payout_cross_stage_idx", def = "{'toId': 1, 'orders._id': 1}")
class MessengerPayout(
    toId: String,
    toName: String,
    toBankName: String,
    toType: BankAccType,
    toAccountNumber: String,
    toBranchCode: String,
    fromReference: String,
    toReference: String,
    emailNotify: String?,
    orders: MutableSet<Order>,
    emailAddress: String?,
    bundleId: String? = null,
    emailSubject: String?,
    tips: MutableSet<Tip>? = null,
    payoutStage: PayoutStage = PayoutStage.PENDING
) : Payout(
    toId = toId, toName = toName, toType = toType, toBankName = toBankName, toAccountNumber = toAccountNumber,
    toBranchCode = toBranchCode, fromReference = fromReference, toReference = toReference, emailNotify = emailNotify,
    emailAddress = emailAddress, emailSubject = emailSubject, orders = orders, bundleId = bundleId, tips = tips,
    payoutStage = payoutStage
) {
    override var paid: Boolean = false
    var isPermEmployed: Boolean = false

    override val total: BigDecimal get() = if (isPermEmployed) orders.sumOf { (it.tip ?: 0.00) }.toBigDecimal()
                                        else orders.sumOf { it.shippingData?.fee!! + (it.tip ?: 0.00) }.toBigDecimal()
}

/**
 * ADR-028 / GAP-2: bare @Document pins the collection name to the Spring Data MongoDB
 * default (uncapitalised simple class name = "shopPayout"), which is the name that
 * was already in use before this annotation was added.  The compound index enables the
 * cross-stage double-pay guard query {toId, orders._id} to execute as an index scan
 * rather than a collection scan on every scheduler retry.
 *
 * "orders._id" maps to Order.id because Spring Data MongoDB serialises the @Id-annotated
 * field of an embedded subdocument as "_id" (see BaseModel.kt line 11-12).
 */
@Document
@CompoundIndex(name = "shop_payout_cross_stage_idx", def = "{'toId': 1, 'orders._id': 1}")
class ShopPayout(
    toId: String,
    toName: String,
    toBankName: String,
    toType: BankAccType,
    toAccountNumber: String,
    orders: MutableSet<Order>,
    toBranchCode: String,
    fromReference: String,
    toReference: String,
    emailNotify: String?,
    emailAddress: String?,
    emailSubject: String?,
    bundleId: String? = null,
    payoutStage: PayoutStage = PayoutStage.PENDING
) : Payout(
    toId = toId, toName = toName, toType = toType, toBankName = toBankName, toAccountNumber = toAccountNumber,
    toBranchCode = toBranchCode, fromReference = fromReference, toReference = toReference,
    emailNotify = emailNotify, emailAddress = emailAddress, emailSubject = emailSubject, orders = orders, bundleId = bundleId,
    payoutStage = payoutStage) {

    override val total: BigDecimal get() = orders.sumOf { it.basketAmount }.toBigDecimal()
    override var paid: Boolean = false
}

class AmbassadorPayout(
    toId: String,
    toName: String,
    toBankName: String,
    toType: BankAccType,
    toAccountNumber: String,
    toBranchCode: String,
    fromReference: String,
    toReference: String,
    emailNotify: String?,
    emailAddress: String?,
    emailSubject: String?,
    orders: MutableSet<Order> = mutableSetOf(),
    bundleId: String? = null,
    payoutStage: PayoutStage = PayoutStage.PENDING,
    var commissionAmount: BigDecimal,
    var triggerDriverId: String,
    var triggerDriverVehicleType: String? = null
) : Payout(
    toId = toId, toName = toName, toType = toType, toBankName = toBankName, toAccountNumber = toAccountNumber,
    toBranchCode = toBranchCode, fromReference = fromReference, toReference = toReference, emailNotify = emailNotify,
    emailAddress = emailAddress, emailSubject = emailSubject, orders = orders, bundleId = bundleId,
    payoutStage = payoutStage
) {
    override var paid: Boolean = false
    override val total: BigDecimal get() = commissionAmount
}

/**
 * RP-009: Payout entity for all three Referral Partner commission types.
 * A single entity (not three subclasses) discriminates via [commissionType].
 *
 * Deduplication is enforced by a compound unique index on (commissionType, triggerReferenceId).
 * For RP-006 triggerReferenceId = customerId; for RP-007/RP-008 it is storeId.
 */
@Document(collection = "referral_partner_payouts")
@CompoundIndexes(
    CompoundIndex(name = "commission_trigger_uidx", def = "{'commissionType': 1, 'triggerReferenceId': 1}", unique = true)
)
class ReferralPartnerPayout(
    toId: String,
    toName: String,
    toBankName: String,
    toType: BankAccType,
    toAccountNumber: String,
    toBranchCode: String,
    fromReference: String,
    toReference: String,
    emailNotify: String?,
    emailAddress: String?,
    emailSubject: String?,
    orders: MutableSet<Order> = mutableSetOf(),
    bundleId: String? = null,
    payoutStage: PayoutStage = PayoutStage.PENDING,
    var commissionAmount: BigDecimal,
    var commissionType: ReferralCommissionType,
    /** customerId (RP-006) or storeId (RP-007/RP-008) — forms part of compound unique index. */
    var triggerReferenceId: String
) : Payout(
    toId = toId, toName = toName, toType = toType, toBankName = toBankName, toAccountNumber = toAccountNumber,
    toBranchCode = toBranchCode, fromReference = fromReference, toReference = toReference, emailNotify = emailNotify,
    emailAddress = emailAddress, emailSubject = emailSubject, orders = orders, bundleId = bundleId,
    payoutStage = payoutStage
) {
    override var paid: Boolean = false
    override val total: BigDecimal get() = commissionAmount
}

enum class PayoutStage {
    PENDING, PROCESSING, COMPLETED, VOIDED
}

fun generateId(): String {
    val random = SecureRandom()
    val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val sb = StringBuilder()
    while (sb.length < 5) {
        sb.append(characters[random.nextInt(characters.length)])
    }
    return sb.substring(0, 5).uppercase(Locale.getDefault())
}
