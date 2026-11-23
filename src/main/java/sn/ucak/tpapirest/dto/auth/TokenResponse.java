package sn.ucak.tpapirest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Jeton d'accès émis après une connexion réussie")
public record TokenResponse(

        @Schema(description = "JWT signé (HS256) à transmettre dans l'en-tête Authorization",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")
        String accessToken,

        @Schema(description = "Type de jeton", example = "Bearer")
        String tokenType,

        @Schema(description = "Durée de validité en secondes", example = "900")
        long expiresIn) {

    public static final String BEARER = "Bearer";

    public static TokenResponse bearer(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, BEARER, expiresIn);
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
