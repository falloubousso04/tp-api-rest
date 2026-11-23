package sn.ucak.tpapirest.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Email obligatoire, au format valide et d'au plus 254 caractères.
 * La valeur contrôlée est déjà normalisée (espaces retirés, minuscules).
 */
@Documented
@Constraint(validatedBy = ValidEmailValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ValidEmail {

    int MAX_LENGTH = 254;

    String message() default "L'email est invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
