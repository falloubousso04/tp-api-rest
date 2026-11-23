package sn.ucak.tpapirest.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Les deux bornes (8 et 72 caractères) sont contrôlées séparément, avec un message distinct. */
public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.codePointCount(0, value.length());
        if (length < ValidPassword.MIN_LENGTH) {
            return ConstraintMessages.reject(context,
                    "Le mot de passe doit contenir au moins " + ValidPassword.MIN_LENGTH + " caractères");
        }
        if (length > ValidPassword.MAX_LENGTH) {
            return ConstraintMessages.reject(context,
                    "Le mot de passe doit contenir au plus " + ValidPassword.MAX_LENGTH + " caractères");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > ValidPassword.MAX_BCRYPT_BYTES) {
            return ConstraintMessages.reject(context, "Le mot de passe ne doit pas dépasser "
                    + ValidPassword.MAX_BCRYPT_BYTES + " octets une fois encodé en UTF-8 (limite de BCrypt)");
        }
        return true;
    }
}
