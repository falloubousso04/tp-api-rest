package sn.ucak.tpapirest.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import sn.ucak.tpapirest.entity.Role;

/** Représentation publique d'un compte : ne contient jamais le mot de passe ni son hachage. */
@Schema(description = "Compte utilisateur")
public record UserResponse(

        @Schema(description = "Identifiant généré par la base", example = "1")
        Long id,

        @Schema(example = "Awa Diop")
        String name,

        @Schema(example = "awa.diop@exemple.test")
        String email,

        @Schema(example = "USER")
        Role role) {
}
