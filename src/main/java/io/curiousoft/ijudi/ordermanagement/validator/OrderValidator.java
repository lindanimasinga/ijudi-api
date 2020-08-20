package io.curiousoft.ijudi.ordermanagement.validator;

import io.curiousoft.ijudi.ordermanagement.model.Order;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;

public class OrderValidator implements ConstraintValidator<Valid, Order> {

    @Override
    public boolean isValid(Order value, ConstraintValidatorContext context) {
        return value.getShippingData() != null;
    }
}
