package sn.ucak.tpapirest.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Paramètres JWT, lus depuis les variables d'environnement {@code JWT_SECRET_BASE64}
 * et {@code JWT_TTL_SECONDS}. Aucune valeur de secours n'est définie pour la clé.
 *
 * @param secretBase64 clé de signature HS256 encodée en Base64 (au moins 32 octets une fois décodée)
 * @param ttlSeconds   durée de validité des tokens, en secondes
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secretBase64,
        @Positive long ttlSeconds) {

    @Override
    public String toString() {
        return "JwtProperties[secretBase64=***, ttlSeconds=" + ttlSeconds + "]";
    }
}
