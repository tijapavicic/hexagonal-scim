package com.example.user.core;

public class ProductCatalogModificationNotAllowedException extends RuntimeException {
    public ProductCatalogModificationNotAllowedException(String message) {
        super(message);
    }
}

