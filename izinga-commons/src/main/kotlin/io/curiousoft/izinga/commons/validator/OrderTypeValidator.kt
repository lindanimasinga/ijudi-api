package io.curiousoft.izinga.commons.validator

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderType
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class OrderTypeValidator : ConstraintValidator<ValidOrderType?, Order> {
    override fun isValid(value: Order, context: ConstraintValidatorContext): Boolean {
        val isInstoreOrder = value.orderType == OrderType.INSTORE
        val isOnlineOrder = value.shippingData != null && value.orderType == OrderType.ONLINE
        return isInstoreOrder || isOnlineOrder
    }
}