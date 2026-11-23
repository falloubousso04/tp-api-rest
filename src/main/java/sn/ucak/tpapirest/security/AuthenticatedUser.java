package sn.ucak.tpapirest.security;

import sn.ucak.tpapirest.entity.Role;

/**
 * Utilisateur authentifié par JWT pour la requête en cours. Construit à partir du compte
 * relu en base : il reflète donc le rôle actuel. Ne contient aucun secret.
 */
public record AuthenticatedUser(Long id, String email, Role role) {
}
