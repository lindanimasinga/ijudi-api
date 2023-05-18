package io.curiousoft.izinga.commons.validator

import java.lang.annotation.Documented
import javax.validation.*
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [OrderTypeValidator::class])
@Documented
annotation class ValidOrderType(
    val message: String = "Please supply shipping info for delivery or If you are paying in store, shipping should be null",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)