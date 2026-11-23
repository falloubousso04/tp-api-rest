package sn.ucak.tpapirest.validation;

import jakarta.validation.ConstraintValidatorContext;

/** Remplace le message par défaut d'une contrainte par un message précis. */
final class ConstraintMessages {

    private ConstraintMessages() {
    }

    static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(escape(message)).addConstraintViolation();
        return false;
    }

    /** Les messages sont des constantes : on neutralise les caractères spéciaux de Bean Validation. */
    private static String escape(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
