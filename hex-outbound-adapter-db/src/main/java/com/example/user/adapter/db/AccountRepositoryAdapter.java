package com.example.user.adapter.db;

import com.example.user.model.Account;
import com.example.user.port.out.AccountRepositoryPort;

import java.util.List;
import java.util.Optional;

public class AccountRepositoryAdapter implements AccountRepositoryPort {

    private final AccountJpaRepository accountJpaRepository;

    public AccountRepositoryAdapter(AccountJpaRepository accountJpaRepository) {
        this.accountJpaRepository = accountJpaRepository;
    }

    @Override
    public Account save(Account account) {
        AccountEntity saved = accountJpaRepository.save(new AccountEntity(account.userId(), account.name()));
        return new Account(saved.getId(), saved.getUserId(), saved.getName());
    }

    @Override
    public List<Account> findAllByUserId(Long userId) {
        return accountJpaRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(entity -> new Account(entity.getId(), entity.getUserId(), entity.getName()))
                .toList();
    }

    @Override
    public Optional<Account> findByIdAndUserId(Long id, Long userId) {
        return accountJpaRepository.findByIdAndUserId(id, userId)
                .map(entity -> new Account(entity.getId(), entity.getUserId(), entity.getName()));
    }

    @Override
    public boolean existsByUserIdAndName(Long userId, String name) {
        return accountJpaRepository.existsByUserIdAndNameIgnoreCase(userId, name);
    }

    @Override
    public void deleteById(Long id) {
        accountJpaRepository.deleteById(id);
    }
}

