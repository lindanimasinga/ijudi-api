package io.curiousoft.izinga.commons.model

import javax.validation.constraints.NotEmpty

class Basket {
    var id: String? = null
    var items: @NotEmpty(message = "order basket is empty") MutableList<BasketItem> = ArrayList()
    val totalPrice: Double
        get() = items.sumOf { it.totalPrice }
    val totalDiscount: Double
        get() = items.sumOf { it.discountedPrice }
}