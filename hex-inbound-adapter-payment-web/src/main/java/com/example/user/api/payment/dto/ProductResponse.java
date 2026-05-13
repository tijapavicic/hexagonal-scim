package com.example.user.api.payment.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        int stockQuantity
) {
}

