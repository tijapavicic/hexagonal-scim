package com.example.user.adapter.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {
    List<AccountEntity> findByUserIdOrderByIdAsc(Long userId);

    Optional<AccountEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}

