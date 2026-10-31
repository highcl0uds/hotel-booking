package ru.hotelbooking.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Строка не длиннее {@link #value()} байт в UTF-8. Нужна там, где предел задан в байтах, а не в символах: например,
 * BCrypt принимает не больше 72 байт, а кириллический символ занимает два.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "must not exceed {value} bytes in UTF-8";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
