package com.example.user.adapter.payment.db;

import com.example.user.model.Payment;
import com.example.user.port.out.PaymentRepositoryPort;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public class PaymentRepositoryAdapter implements PaymentRepositoryPort {
    private final PaymentJpaRepository paymentJpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository paymentJpaRepository) {
        this.paymentJpaRepository = paymentJpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity;
        if (payment.id() == null) {
            entity = new PaymentEntity(
                    payment.productId(),
                    payment.quantity(),
                    payment.totalAmount(),
                    payment.currency(),
                    payment.status(),
                    payment.paymentMethod()
            );
        } else {
            entity = paymentJpaRepository.findById(payment.id())
                    .orElseThrow(() -> new IllegalStateException("Payment entity not found for id: " + payment.id()));
            entity.setProductId(payment.productId());
            entity.setQuantity(payment.quantity());
            entity.setTotalAmount(payment.totalAmount());
            entity.setCurrency(payment.currency());
            entity.setStatus(payment.status());
            entity.setPaymentMethod(payment.paymentMethod());
        }
        PaymentEntity saved = paymentJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Payment> findAll() {
        return paymentJpaRepository.findAll(Sort.by("id").ascending()).stream()
                .map(this::toDomain)
                .toList();
    }


    private Payment toDomain(PaymentEntity entity) {
        return new Payment(
                entity.getId(),
                entity.getProductId(),
                entity.getQuantity(),
                entity.getTotalAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getPaymentMethod()
        );
    }
}

