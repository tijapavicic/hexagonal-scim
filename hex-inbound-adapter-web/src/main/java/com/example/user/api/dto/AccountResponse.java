package com.example.user.api.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        Long userId,
        String name,
        BigDecimal balance
) {
}

