package sn.ucak.tpapirest.validation;

import java.util.Locale;

/**
 * Normalisation des champs texte, appliquée avant la validation et le stockage.
 * Le mot de passe n'est jamais normalisé.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Supprime les espaces (au sens Unicode) aux extrémités ; {@code null} reste {@code null}. */
    public static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /**
     * Supprime les espaces aux extrémités puis convertit en minuscules avec {@link Locale#ROOT},
     * pour un résultat indépendant de la langue du système (ex. le « I » turc).
     */
    public static String normalizeEmail(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
