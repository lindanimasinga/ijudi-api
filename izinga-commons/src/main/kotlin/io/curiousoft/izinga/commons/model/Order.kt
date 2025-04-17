package io.curiousoft.izinga.commons.model

import io.curiousoft.izinga.commons.validator.ValidDeliveryInfo
import io.curiousoft.izinga.commons.validator.ValidOrderType
import org.springframework.data.mongodb.core.mapping.Document
import java.lang.StringBuilder
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

@ValidOrderType
@ValidDeliveryInfo
@Document
class Order : BaseModel() {
    var paymentTypesAllowed: MutableList<PaymentType>? = null
    var stage: @NotNull(message = "order stage is not valid") OrderStage? = null
    var shippingData: ShippingData? = null
    lateinit var basket: @Valid @NotNull(message = "order basket is not valid") Basket
    var customerId: @NotBlank(message = "order customer is not valid") String? = null
    lateinit var shopId: @NotBlank(message = "order shop is not valid") String
    var description: @NotBlank(message = "order description is not valid") String? = null
    var paymentType: PaymentType? = null
    var orderType: @NotNull(message = "order type is not valid") OrderType? = null
    var hasVat = false
    var shopPaid = false
    var shopPaidDate: Date? = null
    var serviceFee = 0.0
    var tip: Double? = null
    var messengerPaid = false
    var messengerPaidDate: Date? = null
    var smsSentToShop = false
    var smsSentToAdmin = false
    var freeDelivery = false
    var minimumDepositAllowedPerc = 0.0
    var payoutCreated = false
    var documents: Set<DocumentAttachment>? = null
    val totalAmount: Double
        get() = BigDecimal.valueOf(
            serviceFee + basket.totalPrice + (!freeDelivery && shippingData != null).isTrue({shippingData?.fee!!}) { 0.00 }
        )
        .setScale(2, RoundingMode.HALF_EVEN)
        .toDouble()

    override fun equals(obj: Any?): Boolean {
        return obj is Order && id == obj.id
    }

    val basketAmount: Double
        get() = BigDecimal.valueOf(basket.items
            .sumOf { obj: BasketItem -> obj.totalPrice }
        )
            .setScale(2, RoundingMode.HALF_EVEN)
            .toDouble()
    val depositAmount: Double
        get() = BigDecimal.valueOf(serviceFee
                + basket.items.sumOf { obj: BasketItem -> obj.totalPrice } * minimumDepositAllowedPerc + (!freeDelivery && shippingData != null).isTrue({ shippingData?.fee!! }) { 0.00 }
        )
            .setScale(2, RoundingMode.HALF_EVEN)
            .toDouble()
}

fun <R> Boolean.isTrue(isTrue: () -> R, isFalse : () -> R): R = if (this) isTrue.invoke() else isFalse.invoke()

fun generateId(): String {
    val random = SecureRandom()
    val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val sb = StringBuilder()
    while (sb.length < 5) {
        sb.append(characters[random.nextInt(characters.length)])
    }
    return sb.substring(0, 5).uppercase(Locale.getDefault())
}