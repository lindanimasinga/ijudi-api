package io.curiousoft.ijudi.ordermanagent.validator;

import io.curiousoft.ijudi.ordermanagent.model.ShippingData;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ShippingValidator implements ConstraintValidator<ValidDeliveryInfo, ShippingData> {

        @Override
        public boolean isValid(ShippingData value, ConstraintValidatorContext context) {
            return value != null && (value.getType() == ShippingData.ShippingType.COLLECTION &&
                    value.getPickUpTime() != null || value.getMessenger() != null);
        }
}
