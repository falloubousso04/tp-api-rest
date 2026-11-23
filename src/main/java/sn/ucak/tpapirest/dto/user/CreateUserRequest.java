package sn.ucak.tpapirest.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.validation.TextNormalizer;
import sn.ucak.tpapirest.validation.ValidEmail;
import sn.ucak.tpapirest.validation.ValidName;
import sn.ucak.tpapirest.validation.ValidPassword;

@Schema(description = "Création d'un compte par un administrateur, avec son rôle")
public record CreateUserRequest(

        @Schema(description = "Nom (2 à 100 caractères, espaces aux extrémités retirés)",
                example = "Moussa Ndiaye", requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidName
        String name,

        @Schema(description = "Email unique (normalisé en minuscules, 254 caractères au plus)",
                example = "moussa.ndiaye@exemple.test", requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidEmail
        String email,

        @Schema(description = "Mot de passe de 8 à 72 caractères, conservé tel quel (espaces compris)",
                example = "Motdepasse-fictif-2026", format = "password",
                minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Le mot de passe est obligatoire")
        @ValidPassword
        String password,

        @Schema(description = "Rôle attribué", example = "USER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Le rôle est obligatoire (USER ou ADMIN)")
        Role role) {

    public CreateUserRequest {
        name = TextNormalizer.strip(name);
        email = TextNormalizer.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "CreateUserRequest[name=" + name + ", email=" + email + ", password=***, role=" + role + "]";
    }
}
