package sn.ucak.tpapirest.dto.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Format commun de toutes les erreurs de l'API (contrôleurs et Spring Security).
 * {@code errors} n'apparaît que pour les erreurs portant sur des champs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Erreur renvoyée par l'API")
public record ApiError(

        @Schema(description = "Statut HTTP", example = "400")
        int status,

        @Schema(description = "Message lisible", example = "Données invalides")
        String message,

        @Schema(description = "Erreurs par champ (présent uniquement pour les erreurs de champs)",
                example = "{\"price\": \"Le prix doit être strictement positif\"}")
        Map<String, String> errors,

        @Schema(description = "Chemin de la requête", example = "/api/products")
        String path,

        @Schema(description = "Date de l'erreur (UTC)", example = "2026-11-24T10:15:30Z")
        Instant timestamp) {

    public static ApiError of(int status, String message, String path) {
        return new ApiError(status, message, Map.of(), path, Instant.now());
    }

    public static ApiError of(int status, String message, Map<String, String> errors, String path) {
        return new ApiError(status, message, errors, path, Instant.now());
    }
}
