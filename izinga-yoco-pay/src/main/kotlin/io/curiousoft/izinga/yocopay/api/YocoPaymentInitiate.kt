package io.curiousoft.izinga.yocopay.api

import io.curiousoft.izinga.yocopay.PaymentMetadata
import java.math.BigDecimal

data class YocoPaymentInitiate(var amount: BigDecimal, val currency: String = "ZAR", val successUrl: String,
                               val metadata: PaymentMetadata, val lineItems: List<YocoLineItem>? = null,
                               val processingMode: String? = null,);

data class YocoLineItem(
    val displayName: String,
    val quantity: Int,
    val pricingDetails: YocoPricingDetails,
    val description: String? = null)

data class YocoPricingDetails(
    val price: BigDecimal
)
