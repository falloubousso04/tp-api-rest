package sn.ucak.tpapirest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.ucak.tpapirest.dto.product.ProductRequest;
import sn.ucak.tpapirest.dto.product.ProductResponse;
import sn.ucak.tpapirest.entity.Product;
import sn.ucak.tpapirest.exception.ResourceNotFoundException;
import sn.ucak.tpapirest.mapper.ProductMapper;
import sn.ucak.tpapirest.repository.ProductRepository;

/** Règles métier du catalogue. Les données reçues sont déjà validées. */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponse> findAll() {
        return productRepository.findAll(Sort.by("id")).stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    public ProductResponse findById(Long id) {
        return ProductMapper.toResponse(getProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product(request.name(), request.description(), toStoredPrice(request.price()));
        return ProductMapper.toResponse(productRepository.save(product));
    }

    /** Un PUT ne crée jamais de produit : un identifiant absent entraîne 404. */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        product.update(request.name(), request.description(), toStoredPrice(request.price()));
        return ProductMapper.toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void delete(Long id) {
        productRepository.delete(getProduct(id));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.product(id));
    }

    /**
     * Aligne l'échelle sur celle de la colonne (2 décimales) sans jamais arrondir :
     * le prix validé a au plus deux décimales, {@link RoundingMode#UNNECESSARY} garantit
     * qu'aucune valeur n'est modifiée (12.3 devient 12.30, même valeur).
     */
    private static BigDecimal toStoredPrice(BigDecimal price) {
        return price.setScale(Product.PRICE_FRACTION_DIGITS, RoundingMode.UNNECESSARY);
    }
}
