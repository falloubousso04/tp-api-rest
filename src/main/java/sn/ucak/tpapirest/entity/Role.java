package sn.ucak.tpapirest.entity;

/**
 * Rôles applicatifs. Stockés en base par leur libellé ({@code USER}, {@code ADMIN}),
 * jamais par leur rang ordinal.
 */
public enum Role {
    USER,
    ADMIN;

    /** Autorité Spring Security correspondante (préfixe {@code ROLE_} attendu par {@code hasRole}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
