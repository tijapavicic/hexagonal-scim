package com.example.user.core;

import com.example.user.model.Product;
import com.example.user.port.in.CreateProductPort;
import com.example.user.port.in.DeleteProductPort;
import com.example.user.port.in.GetAllProductsPort;
import com.example.user.port.in.GetProductPort;
import com.example.user.port.in.UpdateProductPort;
import com.example.user.port.out.ProductRepositoryPort;

import java.math.BigDecimal;
import java.util.List;

public class ProductService
        implements CreateProductPort, GetProductPort, GetAllProductsPort, UpdateProductPort, DeleteProductPort {

    private final ProductRepositoryPort productRepositoryPort;

    public ProductService(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = productRepositoryPort;
    }

    @Override
    public Product create(String name, String description, BigDecimal price, String currency, int stockQuantity) {
        if (productRepositoryPort.existsByName(name)) {
            throw new DuplicateProductException("Product already exists for name: " + name);
        }
        return productRepositoryPort.save(new Product(null, name, description, price, currency, stockQuantity));
    }

    @Override
    public Product getById(Long id) {
        return productRepositoryPort.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found for id: " + id));
    }

    @Override
    public List<Product> getAll() {
        return productRepositoryPort.findAll();
    }

    @Override
    public Product update(Long id, String name, String description, BigDecimal price, String currency, int stockQuantity) {
        Product existing = productRepositoryPort.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found for id: " + id));

        if (!existing.name().equalsIgnoreCase(name) && productRepositoryPort.existsByName(name)) {
            throw new DuplicateProductException("Product already exists for name: " + name);
        }

        return productRepositoryPort.update(new Product(id, name, description, price, currency, stockQuantity));
    }

    @Override
    public void deleteById(Long id) {
        if (productRepositoryPort.findById(id).isEmpty()) {
            throw new ProductNotFoundException("Product not found for id: " + id);
        }
        productRepositoryPort.deleteById(id);
    }
}

