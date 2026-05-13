package com.example.user.port.out;

import com.example.user.model.PaymentMethod;

/** Resolves the concrete payment gateway strategy for the selected method. */
public interface PaymentStrategyPort {
    PaymentGatewayPort resolve(PaymentMethod paymentMethod);
}

