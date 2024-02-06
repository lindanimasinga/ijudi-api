package io.curiousoft.izinga.recon.payout

import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.BaseModel
import io.curiousoft.izinga.commons.model.Order
import java.math.BigDecimal

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
    var emailNotify: String,
    var emailAddress: String,
    var emailSubject: String,
    var orders: MutableSet<Order>,
    var tips: MutableSet<Tip>? = null
): BaseModel() {
    abstract var paid: Boolean
    abstract var total: BigDecimal
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
    emailNotify: String,
    orders: MutableSet<Order>,
    emailAddress: String,
    bundleId: String? = null,
    emailSubject: String,
    tips: MutableSet<Tip>? = null ) : Payout(
    toId = toId, toName = toName, toType = toType, toBankName = toBankName, toAccountNumber = toAccountNumber,
    toBranchCode = toBranchCode, fromReference = fromReference, toReference = toReference, emailNotify = emailNotify,
    emailAddress = emailAddress, emailSubject = emailSubject, orders = orders, bundleId = bundleId, tips = tips) {
    override var paid: Boolean = false
    @org.springframework.data.annotation.Transient
    override var total: BigDecimal = orders.sumOf { it.shippingData?.fee!! }.toBigDecimal()
}

class ShopPayout(
    toId: String,
    toName: String,
    toBankName: String,
    toType: BankAccType,
    toAccountNumber: String,
    orders: MutableSet<Order>,
    toBranchCode: String, fromReference: String, toReference: String, emailNotify: String, emailAddress: String,
    emailSubject: String,
    bundleId: String? = null,
) : Payout(
    toId = toId, toName = toName, toType = toType, toBankName = toBankName, toAccountNumber = toAccountNumber,
    toBranchCode = toBranchCode, fromReference = fromReference, toReference = toReference,
    emailNotify = emailNotify, emailAddress = emailAddress, emailSubject = emailSubject, orders = orders, bundleId = bundleId) {

    @org.springframework.data.annotation.Transient
    override var total: BigDecimal = orders.sumOf { it.basketAmount }.toBigDecimal()
    override var paid: Boolean = false
}
