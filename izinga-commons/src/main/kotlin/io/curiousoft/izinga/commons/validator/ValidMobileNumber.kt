package io.curiousoft.izinga.commons.validator

import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import javax.validation.Payload
import kotlin.reflect.KClass

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(
    RetentionPolicy.RUNTIME
)
@Documented
annotation class ValidMobileNumber(
    val message: String = "Mobile not format is not valid. Please put like +27812815577 or 27812815577",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)