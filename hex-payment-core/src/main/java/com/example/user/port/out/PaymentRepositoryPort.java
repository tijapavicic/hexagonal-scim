package com.example.user.port.out;

import com.example.user.model.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepositoryPort {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    List<Payment> findAll();
}

