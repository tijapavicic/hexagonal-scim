package com.example.user.adapter.db;

import com.example.user.core.InsufficientFundsException;
import com.example.user.model.Account;
import com.example.user.port.out.AccountRepositoryPort;
import com.example.user.port.out.CreditAccountPort;
import com.example.user.port.out.DebitAccountPort;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class AccountRepositoryAdapter implements AccountRepositoryPort, DebitAccountPort, CreditAccountPort {

    private final AccountJpaRepository accountJpaRepository;

    public AccountRepositoryAdapter(AccountJpaRepository accountJpaRepository) {
        this.accountJpaRepository = accountJpaRepository;
    }

    @Override
    public Account save(Account account) {
        AccountEntity saved = accountJpaRepository.save(new AccountEntity(account.userId(), account.name(), account.balance()));
        return new Account(saved.getId(), saved.getUserId(), saved.getName(), saved.getBalance());
    }

    @Override
    public List<Account> findAllByUserId(Long userId) {
        return accountJpaRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(entity -> new Account(entity.getId(), entity.getUserId(), entity.getName(), entity.getBalance()))
                .toList();
    }

    @Override
    public Optional<Account> findByIdAndUserId(Long id, Long userId) {
        return accountJpaRepository.findByIdAndUserId(id, userId)
                .map(entity -> new Account(entity.getId(), entity.getUserId(), entity.getName(), entity.getBalance()));
    }

    @Override
    public boolean existsByUserIdAndName(Long userId, String name) {
        return accountJpaRepository.existsByUserIdAndNameIgnoreCase(userId, name);
    }

    @Override
    public void deleteById(Long id) {
        accountJpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void debit(Long accountId, BigDecimal amount) {
        validatePositiveAmount(amount);
        int updatedRows = accountJpaRepository.debitIfEnough(accountId, amount);
        if (updatedRows == 0) {
            throw new InsufficientFundsException("Insufficient funds or account not found for id: " + accountId);
        }
    }

    @Override
    @Transactional
    public void credit(Long accountId, BigDecimal amount) {
        validatePositiveAmount(amount);
        int updatedRows = accountJpaRepository.credit(accountId, amount);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Account not found for id: " + accountId);
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }
}

