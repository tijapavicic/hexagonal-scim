package com.example.user.core;

import com.example.user.model.Account;
import com.example.user.port.in.CreateAccountPort;
import com.example.user.port.in.DeleteAccountPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetUserAccountsPort;
import com.example.user.port.out.AccountRepositoryPort;
import com.example.user.port.out.UserRepositoryPort;

import java.util.List;

public class AccountService implements CreateAccountPort, GetUserAccountsPort, GetAccountPort, DeleteAccountPort {

    private final UserRepositoryPort userRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;

    public AccountService(UserRepositoryPort userRepositoryPort, AccountRepositoryPort accountRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.accountRepositoryPort = accountRepositoryPort;
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

    private void ensureUserExists(Long userId) {
        if (userRepositoryPort.findById(userId).isEmpty()) {
            throw new UserNotFoundException("User not found for id: " + userId);
        }
    }
}

