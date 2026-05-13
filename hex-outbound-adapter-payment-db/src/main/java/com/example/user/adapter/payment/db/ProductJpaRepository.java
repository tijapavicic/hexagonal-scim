package com.example.user.adapter.payment.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
    boolean existsByNameIgnoreCase(String name);
}

