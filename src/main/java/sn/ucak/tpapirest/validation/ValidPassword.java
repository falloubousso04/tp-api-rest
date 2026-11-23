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
 * Mot de passe de 8 à 72 caractères Unicode, contrôlé sans aucune modification
 * (les espaces sont conservés). Une valeur {@code null} est acceptée par cette contrainte :
 * l'obligation éventuelle est exprimée séparément ({@code @NotNull}).
 *
 * <p>BCrypt ne prend en compte que les 72 premiers octets : un mot de passe dont
 * l'encodage UTF-8 dépasse 72 octets est donc également refusé, plutôt que tronqué.</p>
 */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ValidPassword {

    int MIN_LENGTH = 8;
    int MAX_LENGTH = 72;
    int MAX_BCRYPT_BYTES = 72;

    String message() default "Le mot de passe est invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
