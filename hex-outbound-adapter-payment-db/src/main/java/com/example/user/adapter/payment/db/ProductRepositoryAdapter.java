package com.example.user.adapter.payment.db;

import com.example.user.model.Product;
import com.example.user.port.out.ProductRepositoryPort;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class ProductRepositoryAdapter implements ProductRepositoryPort {
    private final ProductJpaRepository productJpaRepository;

    public ProductRepositoryAdapter(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public Product save(Product product) {
        ProductEntity saved = productJpaRepository.save(new ProductEntity(
                product.name(),
                product.description(),
                product.price(),
                product.currency(),
                product.stockQuantity()
        ));
        return toDomain(saved);
    }

    @Override
    @Transactional
    public Product update(Product product) {
        ProductEntity entity = productJpaRepository.findById(product.id())
                .orElseThrow(() -> new IllegalStateException("Product entity not found for id: " + product.id()));
        entity.setName(product.name());
        entity.setDescription(product.description());
        entity.setPrice(product.price());
        entity.setCurrency(product.currency());
        entity.setStockQuantity(product.stockQuantity());
        ProductEntity saved = productJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAll(Sort.by("id").ascending()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(String name) {
        return productJpaRepository.existsByNameIgnoreCase(name);
    }

    @Override
    public void deleteById(Long id) {
        productJpaRepository.deleteById(id);
    }

    private Product toDomain(ProductEntity entity) {
        return new Product(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getCurrency(),
                entity.getStockQuantity()
        );
    }
}

