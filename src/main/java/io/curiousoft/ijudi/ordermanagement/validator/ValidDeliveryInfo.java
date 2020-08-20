package io.curiousoft.ijudi.ordermanagement.validator;

import javax.validation.Constraint;
import javax.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD, TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = ShippingValidator.class)
@Documented
public @interface ValidDeliveryInfo {

    String message() default "Order shipping is null or pickup time or messenger not valid or shipping address not valid";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
