package com.example.user.config;

import com.example.user.core.PaymentService;
import com.example.user.port.in.GetAllPaymentsPort;
import com.example.user.port.in.GetPaymentPort;
import com.example.user.port.in.InitiatePaymentPort;
import com.example.user.port.out.PaymentRepositoryPort;
import com.example.user.port.out.PaymentStrategyPort;
import com.example.user.port.out.ProductRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class PaymentConfig {

    @Bean
    PaymentService paymentServiceBean(
            ProductRepositoryPort productRepositoryPort,
            PaymentRepositoryPort paymentRepositoryPort,
            PaymentStrategyPort paymentStrategyPort,
            @Value("${payment.fx.eur-to-usd:1.10}") BigDecimal eurToUsdRate
    ) {
        return new PaymentService(productRepositoryPort, paymentRepositoryPort, paymentStrategyPort, eurToUsdRate);
    }

    @Bean
    InitiatePaymentPort initiatePaymentPort(PaymentService paymentServiceBean) {
        return paymentServiceBean::initiate;
    }

    @Bean
    GetPaymentPort getPaymentPort(PaymentService paymentServiceBean) {
        return paymentServiceBean::getById;
    }

    @Bean
    GetAllPaymentsPort getAllPaymentsPort(PaymentService paymentServiceBean) {
        return paymentServiceBean::getAll;
    }
}

