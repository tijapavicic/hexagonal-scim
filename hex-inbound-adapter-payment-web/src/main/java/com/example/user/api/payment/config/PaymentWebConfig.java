package com.example.user.api.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApiPaginationProperties.class)
public class PaymentWebConfig {
}

