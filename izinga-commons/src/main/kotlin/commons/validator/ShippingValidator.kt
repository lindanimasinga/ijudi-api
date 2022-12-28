package io.curiousoft.izinga.commons.validator

import io.curiousoft.izinga.commons.model.BuildingType
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.ShippingData
import org.springframework.util.StringUtils
import java.util.*
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class ShippingValidator : ConstraintValidator<ValidDeliveryInfo?, Order> {
    override fun isValid(order: Order, context: ConstraintValidatorContext): Boolean {
        val value = order.shippingData ?: return true
        val isValidBuildingType =
            value.type == ShippingData.ShippingType.SCHEDULED_DELIVERY || value.buildingType == BuildingType.HOUSE ||
                    !StringUtils.isEmpty(value.unitNumber) && !StringUtils.isEmpty(value.buildingName)
        val isValidPickup =
            value.type == ShippingData.ShippingType.SCHEDULED_DELIVERY && value.pickUpTime?.after(
                Date()
            )?: false
        val hasMessengerForDelivery = value.messengerId != null
        return isValidBuildingType && (isValidPickup || hasMessengerForDelivery)
    }
}