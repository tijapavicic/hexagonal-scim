package com.example.user.config;

import com.example.user.adapter.payment.BankTransferGatewayAdapter;
import com.example.user.adapter.payment.IdealGatewayAdapter;
import com.example.user.adapter.payment.PayPalGatewayAdapter;
import com.example.user.adapter.payment.PaymentStrategyRegistry;
import com.example.user.port.out.PaymentGatewayPort;
import com.example.user.port.out.PaymentStrategyPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PaymentStrategyConfig {

    @Bean
    PaymentGatewayPort bankTransferGatewayAdapter() {
        return new BankTransferGatewayAdapter();
    }

    @Bean
    PaymentGatewayPort payPalGatewayAdapter() {
        return new PayPalGatewayAdapter();
    }

    @Bean
    PaymentGatewayPort idealGatewayAdapter() {
        return new IdealGatewayAdapter();
    }

    @Bean
    PaymentStrategyPort paymentStrategyPort(List<PaymentGatewayPort> paymentGateways) {
        return new PaymentStrategyRegistry(paymentGateways);
    }
}

