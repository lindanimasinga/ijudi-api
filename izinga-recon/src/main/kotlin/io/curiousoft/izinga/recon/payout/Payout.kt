package io.curiousoft.izinga.recon.payout

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.BaseModel
import io.curiousoft.izinga.commons.model.Order
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
    var emailSent = false;
}

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

enum class PayoutStage {
    PENDING, PROCESSING, COMPLETED
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
