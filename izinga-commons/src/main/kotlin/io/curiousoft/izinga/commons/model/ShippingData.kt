package io.curiousoft.izinga.commons.model

import java.util.*
import javax.validation.Valid
import javax.validation.constraints.Future
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

class ShippingData {
    var id: String? = null
    var fromAddress: @NotBlank(message = "shipping address not valid") String? = null
    var fromBuildingType: BuildingType? = null
    var fromUnitNumber: String? = null
    var fromFloorLevel: Int? = null
    var fromBuildingName: String? = null
    var fromBuildingHasElevator: Boolean? = null
    var toAddress: @NotBlank(message = "Shipping address not valid") String? = null
    var buildingType: BuildingType? = null
    var unitNumber: String? = null
    var buildingName: String? = null
    var floorLevel: Int? = null
    var buildingHasElevator: Boolean? = null
    var additionalInstructions: String? = null
    var type: @NotNull(message = "shipping type not valid") ShippingType? = null
    var fee: Double = 0.0
    var messengerId: String? = null
    var pickUpTime: @Future(message = "pickup date must be at least 15 minutes ahead") Date? = null
    var distance: Double = 0.0
    var shippingDataGeoData: ShipingGeoData? = null

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

class ShipingGeoData(val fromGeoPoint: @Valid GeoPoint, val toGeoPoint: @Valid GeoPoint, val distance: Double)