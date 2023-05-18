package io.curiousoft.izinga.commons.model

open class PaymentData {
    var fromAccountId: String? = null
    var toAccountId: String? = null
    var amount = 0.0
    var description: String? = null

    constructor()
    constructor(fromAccount: String?, toAccount: String?, amount: Double, description: String?) {
        fromAccountId = fromAccount
        toAccountId = toAccount
        this.amount = amount
        this.description = description
    }
}