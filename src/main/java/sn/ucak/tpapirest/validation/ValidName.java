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
 * Nom obligatoire, non composé uniquement d'espaces, de 2 à 100 caractères.
 * La valeur contrôlée est déjà débarrassée de ses espaces aux extrémités (voir {@link TextNormalizer}).
 */
@Documented
@Constraint(validatedBy = ValidNameValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ValidName {

    int MIN_LENGTH = 2;
    int MAX_LENGTH = 100;

    String message() default "Le nom est invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
