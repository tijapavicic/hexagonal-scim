package com.example.user.port.in;

import com.example.user.model.Payment;
import com.example.user.model.PaymentMethod;

public interface InitiatePaymentPort {
    Payment initiate(Long userId, Long accountId, Long productId, int quantity, PaymentMethod paymentMethod, String requestedCurrency);

    default Payment initiate(Long productId, int quantity, PaymentMethod paymentMethod, String requestedCurrency) {
        return initiate(null, null, productId, quantity, paymentMethod, requestedCurrency);
    }
}

