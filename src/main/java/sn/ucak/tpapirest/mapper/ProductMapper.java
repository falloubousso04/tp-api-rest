package sn.ucak.tpapirest.mapper;

import sn.ucak.tpapirest.dto.product.ProductResponse;
import sn.ucak.tpapirest.entity.Product;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice());
    }
}
