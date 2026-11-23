package sn.ucak.tpapirest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import sn.ucak.tpapirest.validation.TextNormalizer;
import sn.ucak.tpapirest.validation.ValidEmail;
import sn.ucak.tpapirest.validation.ValidName;
import sn.ucak.tpapirest.validation.ValidPassword;

/**
 * Inscription publique. Le rôle n'est pas un champ de ce modèle : le serveur attribue USER
 * et toute propriété {@code role} envoyée est refusée (400).
 */
@Schema(description = "Inscription publique d'un compte. Le rôle USER est attribué par le serveur ; "
        + "envoyer un champ role entraîne une erreur 400.")
public record RegisterRequest(

        @Schema(description = "Nom (2 à 100 caractères, espaces aux extrémités retirés)",
                example = "Awa Diop", requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidName
        String name,

        @Schema(description = "Email unique (normalisé en minuscules, 254 caractères au plus)",
                example = "awa.diop@exemple.test", requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidEmail
        String email,

        @Schema(description = "Mot de passe de 8 à 72 caractères, conservé tel quel (espaces compris)",
                example = "Motdepasse-fictif-2026", format = "password",
                minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Le mot de passe est obligatoire")
        @ValidPassword
        String password) {

    public RegisterRequest {
        name = TextNormalizer.strip(name);
        email = TextNormalizer.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + email + ", password=***]";
    }
}
