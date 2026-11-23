package sn.ucak.tpapirest.controller;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sn.ucak.tpapirest.config.OpenApiConfig;
import sn.ucak.tpapirest.config.OpenApiConfig.ErrorResponses;
import sn.ucak.tpapirest.dto.auth.LoginRequest;
import sn.ucak.tpapirest.dto.auth.RegisterRequest;
import sn.ucak.tpapirest.dto.auth.TokenResponse;
import sn.ucak.tpapirest.dto.error.ApiError;
import sn.ucak.tpapirest.dto.user.UserResponse;
import sn.ucak.tpapirest.service.AuthService;
import sn.ucak.tpapirest.service.UserService;

/** Routes publiques : inscription et connexion. */
@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_AUTH)
@SecurityRequirements // aucune exigence de sécurité : routes explicitement publiques
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Inscrire un compte USER",
            description = "Route publique. Le serveur attribue toujours le rôle USER : la présence d'un champ "
                    + "role entraîne une erreur 400 et aucun compte n'est créé.")
    @ApiResponse(responseCode = "201", description = "Compte créé (en-tête Location vers /api/users/{id})")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "409", ref = ErrorResponses.REF_CONFLICT)
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = userService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/users/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Authentifier et émettre un JWT",
            description = "Route publique. Retourne un token HS256 à coller dans Authorize (sans le préfixe Bearer).")
    @ApiResponse(responseCode = "200", description = "Connexion réussie")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "401", description = "Email inconnu ou mot de passe incorrect (message identique)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = "{\"status\":401,\"message\":\"Email ou mot de passe "
                            + "incorrect\",\"path\":\"/api/auth/login\",\"timestamp\":\"2026-11-24T10:15:30Z\"}")))
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
