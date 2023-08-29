package io.curiousoft.izinga.commons.validator

import io.curiousoft.izinga.commons.utils.isSAMobileNumber
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class MobileNumberValidator : ConstraintValidator<ValidMobileNumber?, String> {
    override fun isValid(value: String, context: ConstraintValidatorContext): Boolean {
        return isSAMobileNumber(value)
    }
}