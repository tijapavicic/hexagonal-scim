package com.example.user.adapter.payment;

import com.example.user.model.Payment;
import com.example.user.model.PaymentMethod;
import com.example.user.model.PaymentStatus;
import com.example.user.port.out.PaymentGatewayPort;

public class BankTransferGatewayAdapter implements PaymentGatewayPort {
    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.BANK_ACCOUNT;
    }

    @Override
    public PaymentStatus process(Payment payment) {
        // Bank transfers are often asynchronous and settled later.
        return PaymentStatus.PENDING;
    }
}

