package com.example.user.model;

import java.math.BigDecimal;

/**
 * Represents a line item in an order.
 *
 * Immutable value object capturing product details at time of order.
 * Uses record semantics: no setters, only factory methods.
 *
 * SOLID: SRP - represents a single line item in order
 *        - does not handle persistence or business logic
 *        - pure data container with immutability guarantee
 */
public record OrderItem(
    Long productId,      // Product being ordered
    Integer quantity,    // Quantity of units
    BigDecimal unitPrice,// Unit price at time of order (snapshot)
    BigDecimal subtotal  // Calculated subtotal: quantity * unitPrice
) {

    /**
     * Compact constructor - validates inputs on every construction.
     */
    public OrderItem {
        // Validate inputs
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId must be positive");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be greater than 0");
        }
        if (subtotal == null || subtotal.signum() <= 0) {
            throw new IllegalArgumentException("subtotal must be greater than 0");
        }
    }

    /**
     * Factory method to create an order item.
     *
     * @param productId product ID
     * @param quantity quantity (must be > 0)
     * @param unitPrice price per unit (must be > 0)
     * @return new OrderItem with calculated subtotal
     * @throws IllegalArgumentException if quantity or unitPrice invalid
     */
    public static OrderItem create(Long productId, Integer quantity, BigDecimal unitPrice) {
        // Validate inputs
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId must be positive");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be greater than 0");
        }

        // Calculate subtotal
        BigDecimal subtotal = unitPrice.multiply(new BigDecimal(quantity));

        return new OrderItem(productId, quantity, unitPrice, subtotal);
    }
}

