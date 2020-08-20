package io.curiousoft.ijudi.ordermanagement.validator;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.OrderType;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class OrderValidator implements ConstraintValidator<ValidOrder, Order> {

    @Override
    public boolean isValid(Order value, ConstraintValidatorContext context) {
        boolean isInstoreOrder = value.getShippingData() == null && value.getOrderType() == OrderType.INSTORE;
        boolean isOnlineOrder = value.getShippingData() != null && value.getOrderType() == OrderType.ONLINE;
        return isInstoreOrder || isOnlineOrder;
    }
}
