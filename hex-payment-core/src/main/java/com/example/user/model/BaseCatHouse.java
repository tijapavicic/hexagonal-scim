package com.example.user.model;

import java.math.BigDecimal;

/**
 * Single sellable product in the current production phase.
 */
public class BaseCatHouse extends Product {
    public static final String PRODUCT_NAME = "BaseCatHouse";
    public static final String PRODUCT_DESCRIPTION = "Starter cat house for one cat";
    public static final BigDecimal UNIT_PRICE_EUR = new BigDecimal("79.99");
    public static final String CURRENCY = "EUR";

    public BaseCatHouse(Long id, int stockQuantity) {
        super(id, PRODUCT_NAME, PRODUCT_DESCRIPTION, UNIT_PRICE_EUR, CURRENCY, stockQuantity);
    }

    public static boolean isSameProduct(Product product) {
        return product != null && PRODUCT_NAME.equalsIgnoreCase(product.name());
    }

    public static BaseCatHouse from(Product product) {
        if (!isSameProduct(product)) {
            throw new IllegalArgumentException("Product is not BaseCatHouse: " + (product == null ? null : product.name()));
        }
        return new BaseCatHouse(product.id(), product.stockQuantity());
    }
}

