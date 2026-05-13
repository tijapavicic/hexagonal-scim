package com.example.user.adapter.payment;

import com.example.user.model.PaymentMethod;
import com.example.user.port.out.PaymentGatewayPort;
import com.example.user.port.out.PaymentStrategyPort;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PaymentStrategyRegistry implements PaymentStrategyPort {
    private final Map<PaymentMethod, PaymentGatewayPort> gatewaysByMethod;

    public PaymentStrategyRegistry(List<PaymentGatewayPort> gateways) {
        this.gatewaysByMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentGatewayPort gateway : gateways) {
            this.gatewaysByMethod.put(gateway.paymentMethod(), gateway);
        }
    }

    @Override
    public PaymentGatewayPort resolve(PaymentMethod paymentMethod) {
        PaymentGatewayPort gateway = gatewaysByMethod.get(paymentMethod);
        if (gateway == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
        }
        return gateway;
    }
}

