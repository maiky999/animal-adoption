package com.wsb.animaladoption.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects addresses from "disposable mailbox" services (10minutemail, mailinator...).
 * Bots use them to confirm accounts created en masse, because such a mailbox costs nothing and needs no sign-up.
 * The list of domains is in disposable-email-domains.txt (core module resources).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DisposableEmailValidator.class)
public @interface NotDisposableEmail {
    String message() default "{validation.email.disposable}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
