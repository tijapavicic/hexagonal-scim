package com.example.user.config;

import com.example.user.adapter.payment.db.PaymentJpaRepository;
import com.example.user.adapter.payment.db.PaymentRepositoryAdapter;
import com.example.user.adapter.payment.db.ProductJpaRepository;
import com.example.user.adapter.payment.db.ProductRepositoryAdapter;
import com.example.user.port.out.PaymentRepositoryPort;
import com.example.user.port.out.ProductRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentDbAdapterConfig {

    @Bean
    ProductRepositoryPort productRepositoryPort(ProductJpaRepository productJpaRepository) {
        return new ProductRepositoryAdapter(productJpaRepository);
    }

    @Bean
    PaymentRepositoryPort paymentRepositoryPort(PaymentJpaRepository paymentJpaRepository) {
        return new PaymentRepositoryAdapter(paymentJpaRepository);
    }
}

