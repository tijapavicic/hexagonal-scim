package com.example.user.port.out;

import com.example.user.model.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepositoryPort {
    Account save(Account account);

    List<Account> findAllByUserId(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    void deleteById(Long id);
}

