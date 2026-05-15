package com.example.user.api.payment.dto;

import com.example.user.model.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for initiating a new payment (product purchase).
 *
 * <p>{@code userId} is optional. In secured runtime, the backend resolves the user
 * from the JWT {@code email} claim and ignores this field. In local/no-security test
 * runtime (no JWT principal), it is used as a fallback to keep compatibility.
 *
 * <p>{@code accountId} is optional. When provided, the resolved userId and this
 * accountId are used to check the account balance and debit it on success.
 * When omitted, the purchase proceeds without an account debit.
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
