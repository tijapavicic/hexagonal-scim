package com.example.user.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Core domain model representing a sellable product.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code name} must be non-null and non-blank</li>
 *   <li>{@code price} must be non-null and non-negative</li>
 *   <li>{@code currency} must be non-null and non-blank (ISO-4217, e.g. "EUR")</li>
 *   <li>{@code stockQuantity} must be >= 0</li>
 * </ul>
 */
public class Product {
    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final String currency;
    private final int stockQuantity;

    public Product(Long id, String name, String description, BigDecimal price, String currency, int stockQuantity) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("stockQuantity must be >= 0");
        }
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.stockQuantity = stockQuantity;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public BigDecimal price() {
        return price;
    }

    public String currency() {
        return currency;
    }

    public int stockQuantity() {
        return stockQuantity;
    }
}

