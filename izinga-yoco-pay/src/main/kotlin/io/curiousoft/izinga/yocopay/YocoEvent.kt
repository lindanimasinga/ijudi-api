package io.curiousoft.izinga.yocopay

import org.codehaus.jackson.annotate.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
class YocoEvent {
    var createdDate: String? = null
    var id: String? = null
    var payload: PaymentData = null
    var type: String? = null

    constructor()
    constructor(id: String, type: String, payload: PaymentData, createdDate: String) {
        this.id = id
        this.type = type
        this.payload = payload
        this.createdDate = createdDate
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class PaymentData {
    var id: String? = null
    var type: String? = null
    var createdDate: String? = null
    var amount: BigDecimal? = null
    var currency: String? = null
    var status: String? = null
    var metadata: PaymentMetadata? = null
    var mode: String? = null

    constructor()
    constructor(amount: BigDecimal, createdDate: String, status: String, id: String, type: String, metadata: PaymentMetadata, currency: String) {
        this.amount = amount
        this.createdDate = createdDate
        this.status = status
        this.id = id
        this.type = type
        this.metadata = metadata
        this.currency = currency
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class PaymentMetadata {
    var checkoutId: String? = null
    var orderId: String? = null
    var productType: String? = null

    constructor()
    constructor(checkoutId: String, orderId: String) {
        this.checkoutId = checkoutId
        this.orderId = orderId
    }
}

