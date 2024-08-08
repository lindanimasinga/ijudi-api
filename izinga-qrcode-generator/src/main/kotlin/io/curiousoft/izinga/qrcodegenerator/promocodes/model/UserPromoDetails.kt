package io.curiousoft.izinga.qrcodegenerator.promocodes.model

import java.math.BigDecimal
import java.time.LocalDateTime

class UserPromoDetails(val userId: String, val promo: String, val verified: Boolean, amount: BigDecimal, expiry: LocalDateTime)
