package io.curiousoft.izinga.commons.validator

import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import javax.validation.*
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = [ShippingValidator::class])
@Documented
annotation class ValidDeliveryInfo(
    val message: String = "Delivery address or building or unit number not correct.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)