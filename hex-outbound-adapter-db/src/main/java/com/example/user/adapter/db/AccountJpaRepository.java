package com.example.user.adapter.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {
    List<AccountEntity> findByUserIdOrderByIdAsc(Long userId);

    Optional<AccountEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountEntity a
            set a.balance = a.balance - :amount
            where a.id = :accountId and a.balance >= :amount
            """)
    int debitIfEnough(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountEntity a
            set a.balance = a.balance + :amount
            where a.id = :accountId
            """)
    int credit(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);
}

