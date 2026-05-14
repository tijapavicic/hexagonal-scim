package com.example.user.api.payment.dto;

import com.example.user.model.PaymentMethod;
import com.example.user.model.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(
        Long id,
        Long userId,
        Long accountId,
        Long productId,
        int quantity,
        BigDecimal totalAmount,
        String currency,
        PaymentStatus status,
        PaymentMethod paymentMethod
) {
}

