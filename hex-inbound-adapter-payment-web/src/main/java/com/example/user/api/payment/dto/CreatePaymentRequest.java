package com.example.user.api.payment.dto;

import com.example.user.model.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
        @NotNull Long productId,
        @Min(1) int quantity,
        @NotNull PaymentMethod paymentMethod
) {
}

