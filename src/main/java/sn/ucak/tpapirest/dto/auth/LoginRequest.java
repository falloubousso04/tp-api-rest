package sn.ucak.tpapirest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import sn.ucak.tpapirest.validation.TextNormalizer;

@Schema(description = "Identifiants de connexion")
public record LoginRequest(

        @Schema(description = "Email du compte (normalisé avant la recherche)",
                example = "awa.diop@exemple.test", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "L'email est obligatoire")
        String email,

        @Schema(description = "Mot de passe, comparé tel quel au hachage BCrypt",
                example = "Motdepasse-fictif-2026", format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Le mot de passe est obligatoire")
        String password) {

    public LoginRequest {
        email = TextNormalizer.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
