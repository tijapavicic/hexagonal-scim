package com.example.user.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TopUpAccountRequest(
        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount
) {
}

