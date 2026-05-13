package com.example.user.port.out;

import com.example.user.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {
    Product save(Product product);

    Product update(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    boolean existsByName(String name);

    void deleteById(Long id);
}

