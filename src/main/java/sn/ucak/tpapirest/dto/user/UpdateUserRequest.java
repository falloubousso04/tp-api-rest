package sn.ucak.tpapirest.dto.user;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.validation.TextNormalizer;
import sn.ucak.tpapirest.validation.ValidEmail;
import sn.ucak.tpapirest.validation.ValidName;
import sn.ucak.tpapirest.validation.ValidPassword;

/**
 * Modification d'un compte par un administrateur.
 *
 * <p>{@code password} est facultatif : s'il est omis, le hachage existant est conservé.
 * S'il est présent, il doit être non {@code null} ({@link Nulls#FAIL} rejette
 * {@code "password": null} dès la lecture du JSON) et valide.</p>
 */
@Schema(description = "Modification d'un compte. password est facultatif : omis, l'ancien mot de passe est conservé.")
public record UpdateUserRequest(

        @Schema(description = "Nom (2 à 100 caractères, espaces aux extrémités retirés)",
                example = "Moussa Ndiaye", requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidName
        String name,

        @Schema(description = "Email unique (normalisé) ; le compte peut conserver le sien",
                example = "moussa.ndiaye@exemple.test", requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidEmail
        String email,

        @Schema(description = "Rôle", example = "ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Le rôle est obligatoire (USER ou ADMIN)")
        Role role,

        @Schema(description = "Nouveau mot de passe (8 à 72 caractères). Ne pas envoyer pour le conserver ; "
                + "null est refusé.", example = "Nouveau-motdepasse-2026", format = "password",
                minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonSetter(nulls = Nulls.FAIL)
        @ValidPassword
        String password) {

    public UpdateUserRequest {
        name = TextNormalizer.strip(name);
        email = TextNormalizer.normalizeEmail(email);
    }

    public boolean hasPassword() {
        return password != null;
    }

    @Override
    public String toString() {
        return "UpdateUserRequest[name=" + name + ", email=" + email + ", role=" + role
                + ", password=" + (hasPassword() ? "***" : "(inchangé)") + "]";
    }
}
