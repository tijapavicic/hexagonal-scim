package com.example.user.api.payment.dto;

import com.example.user.model.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotNull Long userId,
        @NotNull Long accountId,
        @NotNull Long productId,
        @Min(1) int quantity,
        @NotNull PaymentMethod paymentMethod,
        @Size(min = 3, max = 3) String currency
) {
}

