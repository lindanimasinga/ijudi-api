package io.curiousoft.ijudi.ordermanagement.validator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD, TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = OrderTypeValidator.class)
@Documented
public @interface ValidOrderType {

    String message() default "Please supply shipping info for delivery or If you are paying in store, shipping should be null";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
