package sn.ucak.tpapirest.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sn.ucak.tpapirest.config.OpenApiConfig;
import sn.ucak.tpapirest.config.OpenApiConfig.ErrorResponses;
import sn.ucak.tpapirest.dto.user.CreateUserRequest;
import sn.ucak.tpapirest.dto.user.UpdateUserRequest;
import sn.ucak.tpapirest.dto.user.UserResponse;
import sn.ucak.tpapirest.service.UserService;

/**
 * Gestion des comptes, réservée à ADMIN (règle appliquée par Spring Security), y compris
 * lorsqu'un utilisateur souhaite consulter son propre compte.
 */
@RestController
@RequestMapping(path = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_USERS)
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@ApiResponse(responseCode = "401", ref = ErrorResponses.REF_UNAUTHORIZED)
@ApiResponse(responseCode = "403", ref = ErrorResponses.REF_FORBIDDEN)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Lister les utilisateurs (ADMIN)", description = "Retourne un tableau, éventuellement vide.")
    @ApiResponse(responseCode = "200", description = "Liste des comptes")
    public List<UserResponse> list() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un utilisateur (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Compte trouvé")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = ErrorResponses.REF_NOT_FOUND)
    public UserResponse get(@Parameter(description = "Identifiant du compte", example = "1") @PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer un utilisateur et son rôle (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Compte créé (en-tête Location)")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "409", ref = ErrorResponses.REF_CONFLICT)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier un utilisateur (ADMIN)",
            description = "name, email et role sont obligatoires. password est facultatif : omis, le mot de passe "
                    + "actuel est conservé ; fourni, il doit être valide (null refusé). Ne crée jamais de compte.")
    @ApiResponse(responseCode = "200", description = "Compte modifié")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = ErrorResponses.REF_NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = ErrorResponses.REF_CONFLICT)
    public UserResponse update(@Parameter(description = "Identifiant du compte", example = "2") @PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un utilisateur (ADMIN)",
            description = "Les tokens déjà émis pour ce compte sont refusés (401) dès la requête suivante.")
    @ApiResponse(responseCode = "204", description = "Compte supprimé (sans corps)")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = ErrorResponses.REF_NOT_FOUND)
    public void delete(@Parameter(description = "Identifiant du compte", example = "2") @PathVariable Long id) {
        userService.delete(id);
    }
}
