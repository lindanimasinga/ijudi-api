package io.curiousoft.ijudi.ordermanagement.validator;

import io.curiousoft.ijudi.ordermanagement.utils.IjudiUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class MobileNumberValidator implements ConstraintValidator<ValidMobileNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return IjudiUtils.isSAMobileNumber(value);
    }
}
