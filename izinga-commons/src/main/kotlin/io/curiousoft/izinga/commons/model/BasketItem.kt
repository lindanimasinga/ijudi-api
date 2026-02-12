package io.curiousoft.izinga.commons.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

class BasketItem(@JsonProperty("name") var name: String,
                 @JsonProperty("quantity") var quantity: Int,
                 @JsonProperty("price") var price: Double,
                 @JsonProperty("discountPerc") var discountPerc: Double) {

    @JsonIgnore
    var customerId: String? = null
    var externalUrl: String? = null
    var storePrice = 0.0
    var options: List<SelectionOption>? = null

    // weight in kilograms for this single item (default 0.0)
    var weight: Double = 0.0
    var width: Double = 0.0
    var length: Double = 0.0
    var height: Double = 0.0

    val totalPrice: Double
        get() = price * quantity

    val discountedPrice: Double
    get() = -1 * totalPrice * discountPerc


    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as BasketItem
        return name == that.name
    }
}