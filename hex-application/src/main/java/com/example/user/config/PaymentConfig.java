package com.example.user.config;

import com.example.user.core.PaymentService;
import com.example.user.model.User;
import com.example.user.port.in.GetAllPaymentsPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetPaymentPort;
import com.example.user.port.in.InitiatePaymentPort;
import com.example.user.port.in.ResolvePayerPort;
import com.example.user.port.out.DebitAccountPort;
import com.example.user.port.out.PaymentRepositoryPort;
import com.example.user.port.out.PaymentStrategyPort;
import com.example.user.port.out.ProductRepositoryPort;
import com.example.user.port.out.UserRepositoryPort;
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
            GetAccountPort getAccountPort,
            DebitAccountPort debitAccountPort,
            @Value("${payment.fx.eur-to-usd:1.10}") BigDecimal eurToUsdRate
    ) {
        return new PaymentService(
                productRepositoryPort,
                paymentRepositoryPort,
                paymentStrategyPort,
                getAccountPort,
                debitAccountPort,
                eurToUsdRate
        );
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

    /**
     * Cross-domain bridge: resolves a system user ID from an email address.
     * Lives here (the composition root) so that the payment adapter and the
     * user domain remain decoupled from each other.
     */
    @Bean
    ResolvePayerPort resolvePayerPort(UserRepositoryPort userRepositoryPort) {
        return email -> userRepositoryPort.findByEmail(email).map(User::id);
    }
}

