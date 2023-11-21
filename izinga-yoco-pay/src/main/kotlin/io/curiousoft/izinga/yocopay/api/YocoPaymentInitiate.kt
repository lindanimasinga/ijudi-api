package io.curiousoft.izinga.yocopay.api

import io.curiousoft.izinga.yocopay.PaymentMetadata
import java.math.BigDecimal

data class YocoPaymentInitiate(var amount: BigDecimal, val currency: String = "ZAR", val successUrl: String,
                               val metadata: PaymentMetadata)
