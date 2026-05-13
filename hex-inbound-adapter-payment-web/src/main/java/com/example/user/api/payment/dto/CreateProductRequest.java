package com.example.user.api.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Min(0) int stockQuantity
) {
}

