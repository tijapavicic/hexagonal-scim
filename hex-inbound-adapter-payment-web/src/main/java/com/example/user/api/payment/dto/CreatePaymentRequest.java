package com.example.user.api.payment.dto;

import com.example.user.model.PaymentMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for initiating a new payment (product purchase).
 *
 * <p>{@code userId} is intentionally absent — the backend resolves it from the
 * authenticated JWT {@code email} claim.
 *
 * <p>{@code accountId} is optional. When provided, the resolved userId and this
 * accountId are used to check the account balance and debit it on success.
 * When omitted, the purchase proceeds without an account debit.
 */
public record CreatePaymentRequest(
        Long accountId,

        @NotNull(message = "productId is required")
        Long productId,

        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 10_000, message = "quantity must not exceed 10,000 per order")
        int quantity,

        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod,

        @NotNull(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO 4217 uppercase code (e.g. USD, EUR)")
        String currency
) {
}
