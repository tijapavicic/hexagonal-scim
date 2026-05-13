package com.example.user.adapter.payment;

import com.example.user.model.Payment;
import com.example.user.model.PaymentMethod;
import com.example.user.model.PaymentStatus;
import com.example.user.port.out.PaymentGatewayPort;

public class PayPalGatewayAdapter implements PaymentGatewayPort {
    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.PAYPAL;
    }

    @Override
    public PaymentStatus process(Payment payment) {
        return PaymentStatus.COMPLETED;
    }
}

