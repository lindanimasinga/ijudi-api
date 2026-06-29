package io.curiousoft.izinga.qrcodegenerator.promocodes.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.LocalDateTime

@Document
data class PromoCode(
    @Id var code: String,
    val expiryDate: LocalDateTime,
    val percentage: Double?,
    val type: PromoType,
    val maxRedemption: Int,
    val storeId: String? = null,
    val minimumOrderRequired: Int,
    val amount: BigDecimal?
)

enum class PromoType {
    DISCOUNT,
    CASH,
    SIGNUP
}