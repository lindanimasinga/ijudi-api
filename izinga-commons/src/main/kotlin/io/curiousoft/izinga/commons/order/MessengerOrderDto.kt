package io.curiousoft.izinga.commons.order

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.ShippingData

class MessengerOrderDto: Order {
    constructor()
    constructor(order: Order, izingaCommissionPerc: Double = 0.0) {
        this.id = order.id
        this.createdDate = order.createdDate
        this.modifiedDate = order.modifiedDate
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

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null || this::class != obj::class) return false
        if (this.id == null || (obj as MessengerOrderDto).id == null) return false
        return this.id.equals(obj.id)
    }
}

open class ShippingDataDto: ShippingData {
     constructor()
     constructor(shippingData: ShippingData, commissionPerc: Double? = null) {
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

        this.deliveryFee = shippingData.deliveryFee * (1 - (commissionPerc ?: 0.0))
        this.weigthFee = shippingData.weigthFee * (1 - (commissionPerc ?: 0.0))
        this.volumeFee = shippingData.volumeFee * (1 - (commissionPerc ?: 0.0))
        this.labourFee = shippingData.labourFee * (1 - (commissionPerc ?: 0.0))
    }

    override val fee: Double get() = deliveryFee + weigthFee + volumeFee + labourFee
}
