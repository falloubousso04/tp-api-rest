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
import sn.ucak.tpapirest.dto.product.ProductRequest;
import sn.ucak.tpapirest.dto.product.ProductResponse;
import sn.ucak.tpapirest.service.ProductService;

/** Catalogue : lecture pour USER et ADMIN, écriture pour ADMIN (règles appliquées par Spring Security). */
@RestController
@RequestMapping(path = "/api/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_PRODUCTS)
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@ApiResponse(responseCode = "401", ref = ErrorResponses.REF_UNAUTHORIZED)
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Lister les produits (USER ou ADMIN)", description = "Retourne un tableau, éventuellement vide.")
    @ApiResponse(responseCode = "200", description = "Liste des produits")
    public List<ProductResponse> list() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un produit (USER ou ADMIN)")
    @ApiResponse(responseCode = "200", description = "Produit trouvé")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = ErrorResponses.REF_NOT_FOUND)
    public ProductResponse get(@Parameter(description = "Identifiant du produit", example = "1") @PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer un produit (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Produit créé (en-tête Location)")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "403", ref = ErrorResponses.REF_FORBIDDEN)
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier un produit (ADMIN)",
            description = "name et price sont obligatoires ; description peut être absente ou null. "
                    + "Ne crée jamais de produit ; une requête invalide laisse le produit inchangé.")
    @ApiResponse(responseCode = "200", description = "Produit modifié")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "403", ref = ErrorResponses.REF_FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = ErrorResponses.REF_NOT_FOUND)
    public ProductResponse update(@Parameter(description = "Identifiant du produit", example = "1") @PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un produit (ADMIN)")
    @ApiResponse(responseCode = "204", description = "Produit supprimé (sans corps)")
    @ApiResponse(responseCode = "400", ref = ErrorResponses.REF_BAD_REQUEST)
    @ApiResponse(responseCode = "403", ref = ErrorResponses.REF_FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = ErrorResponses.REF_NOT_FOUND)
    public void delete(@Parameter(description = "Identifiant du produit", example = "1") @PathVariable Long id) {
        productService.delete(id);
    }
}
