package io.curiousoft.izinga.qrcodegenerator.promocodes.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document
data class PromoCode(@Id val code: String, val expiryDate: LocalDateTime, val percentage: Double,
                     val type: PromoType, val maxRedemption: Int, val storeId: String? = null)

enum class PromoType {
    DISCOUNT,
    CASH,
    SIGNUP
}