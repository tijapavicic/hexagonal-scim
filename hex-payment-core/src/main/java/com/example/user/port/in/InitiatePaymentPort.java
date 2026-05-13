package com.example.user.port.in;

import com.example.user.model.Payment;
import com.example.user.model.PaymentMethod;

public interface InitiatePaymentPort {
    Payment initiate(Long productId, int quantity, PaymentMethod paymentMethod);
}

