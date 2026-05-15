package com.example.user.api.payment.dto;

import com.example.user.model.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for initiating a new payment (product purchase).
 *
 * <p>{@code userId} and {@code accountId} are optional. When provided, both must be
 * present together — the system will check the account balance and debit it on success.
 * When omitted, the purchase proceeds without an account debit (e.g. guest checkout).
 */
public record CreatePaymentRequest(
        Long userId,
        Long accountId,
        @NotNull Long productId,
        @Min(1) int quantity,
        @NotNull PaymentMethod paymentMethod,
        @Size(min = 3, max = 3) String currency
) {
}

