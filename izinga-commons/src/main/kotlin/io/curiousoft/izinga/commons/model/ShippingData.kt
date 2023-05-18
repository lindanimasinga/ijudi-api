package io.curiousoft.izinga.commons.model

import java.util.*
import javax.validation.constraints.Future
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

class ShippingData {
    var id: String? = null
    var fromAddress: @NotBlank(message = "shipping address not valid") String? = null
    var toAddress: @NotBlank(message = "Shipping address not valid") String? = null
    var buildingType: BuildingType? = null
    var unitNumber: String? = null
    var buildingName: String? = null
    var additionalInstructions: String? = null
    var type: @NotNull(message = "shipping type not valid") ShippingType? = null
    var fee: Double = 0.0
    var messengerId: String? = null
    var pickUpTime: @Future(message = "pickup date must be at least 15 minutes ahead") Date? = null
    var distance: Double = 0.0

    constructor()
    constructor(
        fromAddress: @NotBlank(message = "shipping address not valid") String?,
        toAddress: @NotBlank(message = "shipping destination address id not valid") String?,
        type: @NotNull ShippingType?
    ) {
        this.fromAddress = fromAddress
        this.toAddress = toAddress
        this.type = type
    }

    enum class ShippingType {
        COLLECTION, DELIVERY, SCHEDULED_DELIVERY
    }
}