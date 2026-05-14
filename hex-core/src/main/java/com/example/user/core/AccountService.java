package com.example.user.core;

import com.example.user.model.Account;
import com.example.user.port.in.CreateAccountPort;
import com.example.user.port.in.DeleteAccountPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetUserAccountsPort;
import com.example.user.port.in.TopUpAccountPort;
import com.example.user.port.out.AccountRepositoryPort;
import com.example.user.port.out.CreditAccountPort;
import com.example.user.port.out.UserRepositoryPort;

import java.math.BigDecimal;
import java.util.List;

public class AccountService implements CreateAccountPort, GetUserAccountsPort, GetAccountPort, DeleteAccountPort, TopUpAccountPort {

    private final UserRepositoryPort userRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;
    private final CreditAccountPort creditAccountPort;

    public AccountService(
            UserRepositoryPort userRepositoryPort,
            AccountRepositoryPort accountRepositoryPort,
            CreditAccountPort creditAccountPort
    ) {
        this.userRepositoryPort = userRepositoryPort;
        this.accountRepositoryPort = accountRepositoryPort;
        this.creditAccountPort = creditAccountPort;
    }

    @Override
    public Account create(Long userId, String name) {
        ensureUserExists(userId);
        if (accountRepositoryPort.existsByUserIdAndName(userId, name)) {
            throw new DuplicateAccountException("Account already exists for user " + userId + " with name: " + name);
        }
        return accountRepositoryPort.save(new Account(null, userId, name));
    }

    @Override
    public List<Account> getAllByUserId(Long userId) {
        ensureUserExists(userId);
        return accountRepositoryPort.findAllByUserId(userId);
    }

    @Override
    public Account getById(Long userId, Long accountId) {
        ensureUserExists(userId);
        return accountRepositoryPort.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for id: " + accountId + " and userId: " + userId));
    }

    @Override
    public void deleteById(Long userId, Long accountId) {
        ensureUserExists(userId);
        Account existing = accountRepositoryPort.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for id: " + accountId + " and userId: " + userId));
        accountRepositoryPort.deleteById(existing.id());
    }

    @Override
    public Account topUp(Long userId, Long accountId, BigDecimal amount) {
        ensureUserExists(userId);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }

        Account existing = accountRepositoryPort.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for id: " + accountId + " and userId: " + userId));

        creditAccountPort.credit(existing.id(), amount);

        return accountRepositoryPort.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for id: " + accountId + " and userId: " + userId));
    }

    private void ensureUserExists(Long userId) {
        if (userRepositoryPort.findById(userId).isEmpty()) {
            throw new UserNotFoundException("User not found for id: " + userId);
        }
    }
}

