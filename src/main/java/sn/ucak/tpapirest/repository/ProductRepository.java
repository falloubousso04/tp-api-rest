package sn.ucak.tpapirest.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.ucak.tpapirest.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
