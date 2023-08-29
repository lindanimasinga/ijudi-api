package io.curiousoft.izinga.commons.model

import io.curiousoft.izinga.commons.utils.calculateMarkupPrice
import org.springframework.data.annotation.Transient
import java.util.*
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

class Stock: Comparable<Stock> {
    var id = UUID.randomUUID().toString()
    lateinit var name: @NotBlank(message = "stock name must not be blank") String
    var description: String? = null
    var detailedDescription: String? = null
    var tags: List<String>? = null
    var group: String? = null
    var position = 10000
    var quantity = 0
    var storePrice = 0.0
    var discountPerc = 0.0
    var images: List<String>? = null
    var mandatorySelection: @NotNull(message = "mandatorySelection not valid") MutableList<SelectionOption>? = null
    var optionalSelection: List<SelectionOption>? = null
    var externalUrlPath: String? = null

    @Transient
    private var markupPercentage = 0.0

    constructor()
    constructor(
        name: @NotBlank(message = "stock name must not be blank") String,
        quantity: @Min(value = 0) Int,
        storePrice: @DecimalMin(value = "0.000", message = "stock price must be greater than or equal to 0.001") Double,
        discountPerc: @Min(value = 0) Double,
        mandatorySelection: @NotNull MutableList<SelectionOption>?) {
        this.name = name
        this.quantity = quantity
        this.storePrice = storePrice
        this.discountPerc = discountPerc
        this.mandatorySelection = mandatorySelection
    }

    val price: Double
        get() = if (markupPercentage > 0) calculateMarkupPrice(storePrice, markupPercentage) else storePrice

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val stock = o as Stock
        return name == stock.name
    }

    override fun hashCode(): Int {
        return Objects.hash(name)
    }

    fun setMarkupPercentage(markupPercentage: Double) {
        this.markupPercentage = markupPercentage
    }

    override operator fun compareTo(other: Stock): Int = if(this.hashCode() > other.hashCode()) 1 else -1

}