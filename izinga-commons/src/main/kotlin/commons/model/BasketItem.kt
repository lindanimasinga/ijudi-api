package io.curiousoft.izinga.commons.model

class BasketItem(var name: String, var quantity: Int, var price: Double, var discountPerc: Double) {
    var externalUrl: String? = null
    var storePrice = 0.0
    var options: List<SelectionOption>? = null

    val totalPrice: Double
        get() = price * quantity

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as BasketItem
        return name == that.name
    }
}