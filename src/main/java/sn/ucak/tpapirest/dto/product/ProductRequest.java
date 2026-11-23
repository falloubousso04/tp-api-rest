package sn.ucak.tpapirest.dto.product;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.ucak.tpapirest.entity.Product;
import sn.ucak.tpapirest.validation.TextNormalizer;
import sn.ucak.tpapirest.validation.ValidName;

/**
 * Données d'un produit pour la création (POST) et la modification (PUT).
 *
 * <p>Le prix est un {@link BigDecimal} lu sans arrondi : {@code 1.230} conserve ses trois
 * décimales et est donc refusé par {@link Digits}.</p>
 */
@Schema(description = "Création ou modification d'un produit")
public record ProductRequest(

        @Schema(description = "Nom (2 à 100 caractères, espaces aux extrémités retirés)",
                example = "Clavier", requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidName
        String name,

        @Schema(description = "Description facultative (1000 caractères au plus)",
                example = "Clavier mécanique AZERTY", maxLength = 1000, nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = Product.DESCRIPTION_MAX_LENGTH, message = "La description doit contenir au plus 1000 caractères")
        String description,

        @Schema(description = "Prix strictement positif, au plus deux décimales (point décimal)",
                example = "12.34", minimum = "0.01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Le prix est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "Le prix doit être strictement positif")
        @Digits(integer = Product.PRICE_INTEGER_DIGITS, fraction = Product.PRICE_FRACTION_DIGITS,
                message = "Le prix doit comporter au plus 2 décimales et 10 chiffres avant la virgule")
        BigDecimal price) {

    public ProductRequest {
        name = TextNormalizer.strip(name);
    }
}
