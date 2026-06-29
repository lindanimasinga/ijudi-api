package io.curiousoft.izinga.qrcodegenerator.promocodes.model

import java.math.BigDecimal
import java.time.LocalDateTime

class UserPromoDetails(val userId: String, val promo: String, val verified: Boolean, val amount: BigDecimal, val expiry: LocalDateTime? = null, val orderId: String)
