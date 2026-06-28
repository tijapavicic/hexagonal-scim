package com.example.user.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TopUpAccountRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @DecimalMax(value = "999999999.99", message = "amount must not exceed 999,999,999.99")
        @Digits(integer = 9, fraction = 2, message = "amount must have at most 9 integer and 2 decimal digits")
        BigDecimal amount
) {
}
