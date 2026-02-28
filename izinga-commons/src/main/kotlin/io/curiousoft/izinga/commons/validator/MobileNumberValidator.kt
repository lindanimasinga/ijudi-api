package io.curiousoft.izinga.commons.validator

import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class MobileNumberValidator : ConstraintValidator<ValidMobileNumber?, String> {
    override fun isValid(value: String, context: ConstraintValidatorContext): Boolean {
        return isSAMobileNumber(value)
    }

    fun isSAMobileNumber(number: String?): Boolean {
        if (number == null) return false
        val regex = "(\\+27|27|0)[1-9]\\d{8}"
        return number.matches(regex.toRegex())
    }
}