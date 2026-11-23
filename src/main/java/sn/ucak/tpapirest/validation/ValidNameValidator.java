package sn.ucak.tpapirest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidNameValidator implements ConstraintValidator<ValidName, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return ConstraintMessages.reject(context, "Le nom est obligatoire");
        }
        String stripped = value.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < ValidName.MIN_LENGTH || length > ValidName.MAX_LENGTH) {
            return ConstraintMessages.reject(context, "Le nom doit contenir entre "
                    + ValidName.MIN_LENGTH + " et " + ValidName.MAX_LENGTH
                    + " caractères (espaces aux extrémités exclus)");
        }
        return true;
    }
}
