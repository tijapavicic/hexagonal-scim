package com.example.user.config;

import com.example.user.core.PaymentService;
import com.example.user.port.in.GetAllPaymentsPort;
import com.example.user.port.in.GetPaymentPort;
import com.example.user.port.in.InitiatePaymentPort;
import com.example.user.port.out.PaymentRepositoryPort;
import com.example.user.port.out.PaymentStrategyPort;
import com.example.user.port.out.ProductRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {

    @Bean
    PaymentService paymentServiceBean(
            ProductRepositoryPort productRepositoryPort,
            PaymentRepositoryPort paymentRepositoryPort,
            PaymentStrategyPort paymentStrategyPort
    ) {
        return new PaymentService(productRepositoryPort, paymentRepositoryPort, paymentStrategyPort);
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

