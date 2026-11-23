package sn.ucak.tpapirest.exception;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Traduit une erreur de lecture du corps JSON en erreurs par champ lisibles.
 * Les messages techniques de Jackson (qui peuvent citer le contenu reçu) ne sont jamais renvoyés.
 */
final class JsonErrorTranslator {

    static final String MALFORMED_BODY = "Corps de requête JSON absent, illisible ou mal formé";

    private JsonErrorTranslator() {
    }

    /** Erreurs par champ déduites de l'exception, ou une map vide si le JSON est simplement illisible. */
    static Map<String, String> fieldErrors(Throwable exception) {
        JacksonException jackson = findJacksonException(exception);
        if (jackson == null) {
            return Map.of();
        }
        String field = fieldPath(jackson.getPath());
        if (field.isEmpty()) {
            return Map.of();
        }
        if (jackson instanceof UnrecognizedPropertyException unknown) {
            return Map.of(fieldPathWith(field, unknown.getPropertyName()),
                    "Ce champ n'est pas autorisé dans cette requête");
        }
        if (jackson instanceof InvalidNullException) {
            return Map.of(field, "La valeur null n'est pas autorisée ; omettez le champ pour ne pas le modifier");
        }
        if (jackson instanceof MismatchedInputException mismatch) {
            return Map.of(field, describeExpectedType(mismatch.getTargetType()));
        }
        return Map.of(field, "Valeur invalide");
    }

    private static String describeExpectedType(Class<?> targetType) {
        if (targetType == null) {
            return "Valeur invalide";
        }
        if (targetType.isEnum()) {
            String accepted = Arrays.stream(targetType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "Valeur inconnue. Valeurs acceptées : " + accepted;
        }
        if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive()) {
            return "Valeur invalide : un nombre est attendu (point décimal)";
        }
        if (targetType == String.class) {
            return "Valeur invalide : une chaîne de caractères est attendue";
        }
        return "Valeur invalide";
    }

    private static JacksonException findJacksonException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof JacksonException jackson) {
                return jackson;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String fieldPath(List<JacksonException.Reference> path) {
        StringBuilder builder = new StringBuilder();
        for (JacksonException.Reference reference : path) {
            if (reference.getPropertyName() != null) {
                if (!builder.isEmpty()) {
                    builder.append('.');
                }
                builder.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                builder.append('[').append(reference.getIndex()).append(']');
            }
        }
        return builder.toString();
    }

    /** Pour une propriété inconnue, Jackson place déjà son nom en fin de chemin. */
    private static String fieldPathWith(String path, String propertyName) {
        return path.endsWith(propertyName) ? path : path + "." + propertyName;
    }
}
