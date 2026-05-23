package io.curiousoft.izinga.commons.order

import io.curiousoft.izinga.commons.model.Basket
import io.curiousoft.izinga.commons.model.BuildingType
import io.curiousoft.izinga.commons.model.DocumentAttachment
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.OrderStatusHistory
import io.curiousoft.izinga.commons.model.OrderType
import io.curiousoft.izinga.commons.model.PaymentType
import io.curiousoft.izinga.commons.model.ShipingGeoData
import io.curiousoft.izinga.commons.model.ShippingData
import io.curiousoft.izinga.commons.model.ShippingData.ShippingType
import jakarta.validation.Valid
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.Date
import java.util.stream.Collectors

class MessengerOrderDto(order: Order, izingaCommissionPerc: Double = 0.0): Order() {
    init {
        this.paymentTypesAllowed = order.paymentTypesAllowed
        this.stage = order.stage
        this.shippingData = ShippingDataDto(order.shippingData ?: ShippingData(), izingaCommissionPerc)
        this.basket = order.basket
        this.customerId = order.customerId
        this.shopId = order.shopId
        this.description = order.description
        this.paymentType = order.paymentType
        this.orderType = order.orderType
        this.hasVat = order.hasVat
        this.shopPaid = order.shopPaid
        this.shopPaidDate = order.shopPaidDate
        this.serviceFee = order.serviceFee
        this.tip = order.tip
        this.messengerPaid = order.messengerPaid
        this.messengerPaidDate = order.messengerPaidDate
        this.smsSentToShop = order.smsSentToShop
        this.smsSentToAdmin = order.smsSentToAdmin
        this.scheduledNotified = order.scheduledNotified
        this.freeDelivery = order.freeDelivery
        this.minimumDepositAllowedPerc = order.minimumDepositAllowedPerc
        this.payoutCreated = order.payoutCreated

        if (order.documents != null) {
            this.documents = HashSet(order.documents!!)
        }

        if (order.statusHistory != null) {
            this.statusHistory = ArrayList(order.statusHistory!!)
        }
    }
}

open class ShippingDataDto(shippingData: ShippingData, commissionPerc: Double? = null): ShippingData() {
    init {
        this.id = shippingData.id
        this.fromAddress = shippingData.fromAddress
        this.fromBuildingType = shippingData.fromBuildingType
        this.fromUnitNumber = shippingData.fromUnitNumber
        this.fromFloorLevel = shippingData.fromFloorLevel
        this.fromBuildingName = shippingData.fromBuildingName
        this.fromBuildingHasElevator = shippingData.fromBuildingHasElevator
        this.toAddress = shippingData.toAddress
        this.buildingType = shippingData.buildingType
        this.unitNumber = shippingData.unitNumber
        this.buildingName = shippingData.buildingName
        this.floorLevel = shippingData.floorLevel
        this.buildingHasElevator = shippingData.buildingHasElevator
        this.additionalInstructions = shippingData.additionalInstructions
        this.type = shippingData.type
        this.messengerId = shippingData.messengerId
        this.pickUpTime = shippingData.pickUpTime
        this.distance = shippingData.distance
        this.shippingDataGeoData = shippingData.shippingDataGeoData
        this.category = shippingData.category
        this.izingaCommission = shippingData.izingaCommission

        val deliveryFee: Double = shippingData.deliveryFee * (1 - (commissionPerc ?: 0.0))
        val weigthFee: Double = shippingData.weigthFee * (1 - (commissionPerc ?: 0.0))
        val volumeFee: Double = shippingData.volumeFee * (1 - (commissionPerc ?: 0.0))
        val labourFee: Double = shippingData.labourFee * (1 - (commissionPerc ?: 0.0))
    }

    override val fee: Double get() = deliveryFee + weigthFee + volumeFee + labourFee
}
