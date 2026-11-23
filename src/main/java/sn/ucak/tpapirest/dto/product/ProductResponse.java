package sn.ucak.tpapirest.dto.product;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Produit")
public record ProductResponse(

        @Schema(description = "Identifiant généré par la base", example = "1")
        Long id,

        @Schema(example = "Clavier")
        String name,

        @Schema(example = "Clavier mécanique AZERTY", nullable = true)
        String description,

        @Schema(example = "12.34")
        BigDecimal price) {
}
