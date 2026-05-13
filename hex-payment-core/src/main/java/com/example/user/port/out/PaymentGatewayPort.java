package com.example.user.port.out;

import com.example.user.model.Payment;
import com.example.user.model.PaymentMethod;
import com.example.user.model.PaymentStatus;

/** Strategy interface for a concrete payment channel implementation. */
public interface PaymentGatewayPort {
    PaymentMethod paymentMethod();

    PaymentStatus process(Payment payment);
}

