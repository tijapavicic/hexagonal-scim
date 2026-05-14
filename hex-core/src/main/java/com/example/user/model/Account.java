package com.example.user.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Account(Long id, Long userId, String name, BigDecimal balance) {

    public Account {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("balance must be >= 0");
        }
    }

    public Account(Long id, Long userId, String name) {
        this(id, userId, name, BigDecimal.ZERO);
    }
}

