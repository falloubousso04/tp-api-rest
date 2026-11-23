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
 * S'il est présent, il doit être non {@code null} et valide.</p>
 *
 * <p>Ce DTO est une classe à setters plutôt qu'un record afin de distinguer un champ
 * <em>absent</em> (setter non appelé) d'un champ explicitement {@code null}
 * ({@link Nulls#FAIL} : rejet dès la lecture du JSON, erreur 400).</p>
 */
@Schema(description = "Modification d'un compte. password est facultatif : omis, l'ancien mot de passe est conservé.")
public final class UpdateUserRequest {

    @Schema(description = "Nom (2 à 100 caractères, espaces aux extrémités retirés)",
            example = "Moussa Ndiaye", requiredMode = Schema.RequiredMode.REQUIRED)
    @ValidName
    private String name;

    @Schema(description = "Email unique (normalisé) ; le compte peut conserver le sien",
            example = "moussa.ndiaye@exemple.test", requiredMode = Schema.RequiredMode.REQUIRED)
    @ValidEmail
    private String email;

    @Schema(description = "Rôle", example = "ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Le rôle est obligatoire (USER ou ADMIN)")
    private Role role;

    @Schema(description = "Nouveau mot de passe (8 à 72 caractères). Ne pas envoyer pour le conserver ; "
            + "null est refusé.", example = "Nouveau-motdepasse-2026", format = "password",
            minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @ValidPassword
    private String password;

    /** Utilisé par Jackson. */
    public UpdateUserRequest() {
    }

    /**
     * Construction programmatique ; un {@code password} {@code null} signifie « non fourni ».
     */
    public static UpdateUserRequest of(String name, String email, Role role, String password) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName(name);
        request.setEmail(email);
        request.setRole(role);
        request.password = password;
        return request;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = TextNormalizer.strip(name);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = TextNormalizer.normalizeEmail(email);
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    /** Appelé uniquement si le champ est présent dans le JSON ; {@code null} est refusé par Jackson. */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setPassword(String password) {
        this.password = password;
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
