package sn.ucak.tpapirest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import sn.ucak.tpapirest.dto.error.ApiError;

/**
 * Document OpenAPI affiché par Swagger UI.
 *
 * <p>Le schéma {@value #BEARER_SCHEME} (HTTP, bearer, JWT) est associé aux contrôleurs protégés ;
 * l'inscription et la connexion n'ont aucune exigence de sécurité. Le cadenas de Swagger n'est
 * qu'une indication : les droits réels sont appliqués par Spring Security.</p>
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    public static final String TAG_AUTH = "Authentification";
    public static final String TAG_PRODUCTS = "Produits";
    public static final String TAG_USERS = "Utilisateurs";

    @Bean
    OpenAPI tpApiOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Collez uniquement la valeur accessToken renvoyée par /api/auth/login, "
                                + "sans guillemets ni préfixe : Swagger ajoute « Bearer » automatiquement."));

        ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);
        components
                .addResponses(ErrorResponses.BAD_REQUEST, error("Données invalides ou corps JSON non conforme",
                        "{\"status\":400,\"message\":\"Données invalides\",\"errors\":{\"price\":"
                                + "\"Le prix doit être strictement positif\"},\"path\":\"/api/products\","
                                + "\"timestamp\":\"2026-11-24T10:15:30Z\"}"))
                .addResponses(ErrorResponses.UNAUTHORIZED, error("Token absent, invalide ou expiré",
                        "{\"status\":401,\"message\":\"Token expiré\",\"path\":\"/api/products\","
                                + "\"timestamp\":\"2026-11-24T10:15:30Z\"}"))
                .addResponses(ErrorResponses.FORBIDDEN, error("Rôle insuffisant pour cette opération",
                        "{\"status\":403,\"message\":\"Accès refusé : rôle insuffisant\","
                                + "\"path\":\"/api/users\",\"timestamp\":\"2026-11-24T10:15:30Z\"}"))
                .addResponses(ErrorResponses.NOT_FOUND, error("Ressource inexistante",
                        "{\"status\":404,\"message\":\"Produit introuvable (id 42)\","
                                + "\"path\":\"/api/products/42\",\"timestamp\":\"2026-11-24T10:15:30Z\"}"))
                .addResponses(ErrorResponses.CONFLICT, error("Email déjà utilisé par un autre compte",
                        "{\"status\":409,\"message\":\"Cet email est déjà utilisé par un autre compte\","
                                + "\"errors\":{\"email\":\"Cet email est déjà utilisé\"},"
                                + "\"path\":\"/api/users\",\"timestamp\":\"2026-11-24T10:15:30Z\"}"));

        return new OpenAPI()
                .info(new Info()
                        .title("TP API REST sécurisée — Produits et utilisateurs")
                        .description("""
                                API JSON de gestion des produits et des utilisateurs (Spring Boot, PostgreSQL, JWT).

                                **Parcours** : inscrivez-vous (`POST /api/auth/register`), connectez-vous \
                                (`POST /api/auth/login`), puis cliquez sur **Authorize** et collez la valeur \
                                `accessToken` seule.

                                **Droits** : USER et ADMIN consultent les produits ; seul ADMIN crée, modifie \
                                et supprime des produits et gère les utilisateurs. Les tokens sont valables \
                                15 minutes dans le profil tp.""")
                        .version("1.0.0"))
                .addTagsItem(new Tag().name(TAG_AUTH).description("Inscription et connexion (routes publiques)"))
                .addTagsItem(new Tag().name(TAG_PRODUCTS).description("Catalogue : lecture USER/ADMIN, écriture ADMIN"))
                .addTagsItem(new Tag().name(TAG_USERS).description("Gestion des comptes (ADMIN uniquement)"))
                .components(components);
    }

    private static ApiResponse error(String description, String example) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ApiError");
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(schema).example(example)));
    }

    /** Références vers les réponses d'erreur communes, utilisables dans {@code @ApiResponse(ref = ...)}. */
    public static final class ErrorResponses {

        public static final String BAD_REQUEST = "BadRequest";
        public static final String UNAUTHORIZED = "Unauthorized";
        public static final String FORBIDDEN = "Forbidden";
        public static final String NOT_FOUND = "NotFound";
        public static final String CONFLICT = "Conflict";

        public static final String REF_BAD_REQUEST = "#/components/responses/" + BAD_REQUEST;
        public static final String REF_UNAUTHORIZED = "#/components/responses/" + UNAUTHORIZED;
        public static final String REF_FORBIDDEN = "#/components/responses/" + FORBIDDEN;
        public static final String REF_NOT_FOUND = "#/components/responses/" + NOT_FOUND;
        public static final String REF_CONFLICT = "#/components/responses/" + CONFLICT;

        private ErrorResponses() {
        }
    }
}
