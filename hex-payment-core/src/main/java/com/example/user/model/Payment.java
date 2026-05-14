package com.example.user.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Core domain model representing a payment transaction.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code productId}, {@code totalAmount}, {@code currency}, {@code status},
 *       {@code paymentMethod} must all be non-null</li>
 *   <li>{@code quantity} must be > 0</li>
 *   <li>{@code totalAmount} must be > 0</li>
 * </ul>
 */
public record Payment(
        Long id,
        Long userId,
        Long accountId,
        Long productId,
        int quantity,
        BigDecimal totalAmount,
        String currency,
        PaymentStatus status,
        PaymentMethod paymentMethod
) {
    public Payment {
        Objects.requireNonNull(productId,     "productId must not be null");
        Objects.requireNonNull(totalAmount,   "totalAmount must not be null");
        Objects.requireNonNull(currency,      "currency must not be null");
        Objects.requireNonNull(status,        "status must not be null");
        Objects.requireNonNull(paymentMethod, "paymentMethod must not be null");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("totalAmount must be > 0");
        if (currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
    }

    public Payment(
            Long id,
            Long productId,
            int quantity,
            BigDecimal totalAmount,
            String currency,
            PaymentStatus status,
            PaymentMethod paymentMethod
    ) {
        this(id, null, null, productId, quantity, totalAmount, currency, status, paymentMethod);
    }
}

