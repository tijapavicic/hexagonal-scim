package com.example.user.api.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must not exceed 120 characters")
        @Pattern(regexp = "^[^\\r\\n\\t\\x00-\\x1F\\x7F]*$",
                message = "name must not contain control characters")
        String name,

        @Size(max = 1000, message = "description must not exceed 1000 characters")
        String description,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "price must be >= 0.00")
        @DecimalMax(value = "999999999.99", message = "price must not exceed 999,999,999.99")
        @Digits(integer = 9, fraction = 2, message = "price must have at most 9 integer and 2 decimal digits")
        BigDecimal price,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO 4217 uppercase code (e.g. USD, EUR)")
        String currency,

        @Min(value = 0, message = "stockQuantity must be >= 0")
        @Max(value = 1_000_000, message = "stockQuantity must not exceed 1,000,000")
        int stockQuantity
) {
}
