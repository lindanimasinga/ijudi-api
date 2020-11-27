package io.curiousoft.ijudi.ordermanagement.validator;

import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD, TYPE, PARAMETER })
@Retention(RUNTIME)
@Documented
public @interface ValidMobileNumber {

    String message() default "Mobile not format is not valid. Please put like +27812815577 or 27812815577";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
