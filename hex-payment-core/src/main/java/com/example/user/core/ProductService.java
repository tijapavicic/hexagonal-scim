package com.example.user.core;

import com.example.user.model.BaseCatHouse;
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
        throw new ProductCatalogModificationNotAllowedException(
                "Product catalog is read-only in production phase. Only BaseCatHouse is sold currently.");
    }

    @Override
    public Product getById(Long id) {
        Product product = productRepositoryPort.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found for id: " + id));
        if (!BaseCatHouse.isSameProduct(product)) {
            throw new ProductNotFoundException("Only BaseCatHouse is sellable in the current phase.");
        }
        return BaseCatHouse.from(product);
    }

    @Override
    public List<Product> getAll() {
        return productRepositoryPort.findAll().stream()
                .filter(BaseCatHouse::isSameProduct)
                .map(product -> (Product) BaseCatHouse.from(product))
                .toList();
    }

    @Override
    public Product update(Long id, String name, String description, BigDecimal price, String currency, int stockQuantity) {
        throw new ProductCatalogModificationNotAllowedException(
                "Product catalog is read-only in production phase. BaseCatHouse data is managed via migrations.");
    }

    @Override
    public void deleteById(Long id) {
        throw new ProductCatalogModificationNotAllowedException(
                "Product catalog is read-only in production phase. Deletion is not allowed.");
    }
}

