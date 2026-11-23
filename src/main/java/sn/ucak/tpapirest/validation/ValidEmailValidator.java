package sn.ucak.tpapirest.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Contrôle d'un email selon les limites usuelles (RFC 5321) : partie locale de 64 caractères
 * au plus, labels de domaine de 63 caractères au plus, domaine composé d'au moins deux labels
 * et terminé par une extension alphabétique.
 */
public class ValidEmailValidator implements ConstraintValidator<ValidEmail, String> {

    private static final int LOCAL_PART_MAX_LENGTH = 64;

    private static final String LOCAL_ATOM = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+";

    private static final Pattern LOCAL_PART = Pattern.compile(LOCAL_ATOM + "(\\." + LOCAL_ATOM + ")*");

    private static final Pattern DOMAIN_LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    private static final Pattern TOP_LEVEL_DOMAIN = Pattern.compile("[a-z]{2,63}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return ConstraintMessages.reject(context, "L'email est obligatoire");
        }
        if (value.length() > ValidEmail.MAX_LENGTH) {
            return ConstraintMessages.reject(context,
                    "L'email doit contenir au plus " + ValidEmail.MAX_LENGTH + " caractères");
        }
        if (!hasValidFormat(value)) {
            return ConstraintMessages.reject(context, "Le format de l'email est invalide");
        }
        return true;
    }

    static boolean hasValidFormat(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at == email.length() - 1) {
            return false;
        }
        String localPart = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (localPart.length() > LOCAL_PART_MAX_LENGTH || !LOCAL_PART.matcher(localPart).matches()) {
            return false;
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return TOP_LEVEL_DOMAIN.matcher(labels[labels.length - 1]).matches();
    }
}
