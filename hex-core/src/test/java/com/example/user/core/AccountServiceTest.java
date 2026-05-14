package com.example.user.core;

import com.example.user.model.Account;
import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.out.AccountRepositoryPort;
import com.example.user.port.out.CreditAccountPort;
import com.example.user.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountServiceTest {

    @Test
    void createStoresAccountForExistingUser() {
        InMemoryUserRepo users = new InMemoryUserRepo();
        users.save(new User(null, "john@example.com", "John"));
        InMemoryAccountRepo accounts = new InMemoryAccountRepo();
        AccountService service = new AccountService(users, accounts, new InMemoryCreditPort(accounts));

        Account account = service.create(1L, "Main account");

        assertEquals(1L, account.id());
        assertEquals(1L, account.userId());
        assertEquals("Main account", account.name());
    }

    @Test
    void createRejectsWhenUserMissing() {
        InMemoryAccountRepo accounts = new InMemoryAccountRepo();
        AccountService service = new AccountService(new InMemoryUserRepo(), accounts, new InMemoryCreditPort(accounts));

        assertThrows(UserNotFoundException.class, () -> service.create(99L, "Main account"));
    }

    @Test
    void createRejectsDuplicateAccountNamePerUser() {
        InMemoryUserRepo users = new InMemoryUserRepo();
        users.save(new User(null, "john@example.com", "John"));
        InMemoryAccountRepo accounts = new InMemoryAccountRepo();
        AccountService service = new AccountService(users, accounts, new InMemoryCreditPort(accounts));
        service.create(1L, "Main account");

        assertThrows(DuplicateAccountException.class, () -> service.create(1L, "MAIN ACCOUNT"));
    }

    @Test
    void getAllReturnsAccountsForUser() {
        InMemoryUserRepo users = new InMemoryUserRepo();
        users.save(new User(null, "john@example.com", "John"));
        InMemoryAccountRepo accounts = new InMemoryAccountRepo();
        AccountService service = new AccountService(users, accounts, new InMemoryCreditPort(accounts));
        service.create(1L, "Main account");
        service.create(1L, "Savings");

        List<Account> userAccounts = service.getAllByUserId(1L);

        assertEquals(2, userAccounts.size());
    }

    @Test
    void getByIdThrowsWhenAccountDoesNotBelongToUser() {
        InMemoryUserRepo users = new InMemoryUserRepo();
        users.save(new User(null, "john@example.com", "John"));
        users.save(new User(null, "jane@example.com", "Jane"));
        InMemoryAccountRepo accounts = new InMemoryAccountRepo();
        AccountService service = new AccountService(users, accounts, new InMemoryCreditPort(accounts));
        Account account = service.create(1L, "Main account");

        assertThrows(AccountNotFoundException.class, () -> service.getById(2L, account.id()));
    }

    @Test
    void deleteRemovesAccount() {
        InMemoryUserRepo users = new InMemoryUserRepo();
        users.save(new User(null, "john@example.com", "John"));
        InMemoryAccountRepo accounts = new InMemoryAccountRepo();
        AccountService service = new AccountService(users, accounts, new InMemoryCreditPort(accounts));
        Account account = service.create(1L, "Main account");

        service.deleteById(1L, account.id());

        assertThrows(AccountNotFoundException.class, () -> service.getById(1L, account.id()));
    }

    @Test
    void topUpIncreasesBalance() {
        InMemoryUserRepo users = new InMemoryUserRepo();
        users.save(new User(null, "john@example.com", "John"));
        InMemoryAccountRepo accounts = new InMemoryAccountRepo();
        AccountService service = new AccountService(users, accounts, new InMemoryCreditPort(accounts));
        Account account = service.create(1L, "Main account");

        Account toppedUp = service.topUp(1L, account.id(), new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), toppedUp.balance());
    }

    @Test
    void topUpRejectsNegativeAmount() {
        InMemoryUserRepo users = new InMemoryUserRepo();
        users.save(new User(null, "john@example.com", "John"));
        InMemoryAccountRepo accounts = new InMemoryAccountRepo();
        AccountService service = new AccountService(users, accounts, new InMemoryCreditPort(accounts));
        Account account = service.create(1L, "Main account");

        assertThrows(IllegalArgumentException.class,
                () -> service.topUp(1L, account.id(), new BigDecimal("-1.00")));
    }

    private static final class InMemoryUserRepo implements UserRepositoryPort {
        private final AtomicLong sequence = new AtomicLong(0);
        private final Map<Long, User> store = new HashMap<>();

        @Override
        public User save(User user) {
            long id = sequence.incrementAndGet();
            User saved = new User(id, user.email(), user.displayName());
            store.put(id, saved);
            return saved;
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsByEmail(String email) {
            return store.values().stream().anyMatch(u -> u.email().equalsIgnoreCase(email));
        }

        @Override
        public PagedUsers findAll(int pageNumber, int pageSize) {
            return new PagedUsers(List.of(), 0, 0, 0, 0);
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public User update(User user) {
            store.put(user.id(), user);
            return user;
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }
    }

    private static final class InMemoryAccountRepo implements AccountRepositoryPort {
        private final AtomicLong sequence = new AtomicLong(0);
        private final Map<Long, Account> store = new HashMap<>();

        @Override
        public Account save(Account account) {
            long id = sequence.incrementAndGet();
            Account saved = new Account(id, account.userId(), account.name(), account.balance());
            store.put(id, saved);
            return saved;
        }

        @Override
        public List<Account> findAllByUserId(Long userId) {
            return store.values().stream().filter(a -> a.userId().equals(userId)).toList();
        }

        @Override
        public Optional<Account> findByIdAndUserId(Long id, Long userId) {
            Account account = store.get(id);
            if (account == null || !account.userId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(account);
        }

        @Override
        public boolean existsByUserIdAndName(Long userId, String name) {
            return store.values().stream()
                    .anyMatch(a -> a.userId().equals(userId) && a.name().equalsIgnoreCase(name));
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }

        void update(Account account) {
            store.put(account.id(), account);
        }
    }

    private static final class InMemoryCreditPort implements CreditAccountPort {
        private final InMemoryAccountRepo repo;

        private InMemoryCreditPort(InMemoryAccountRepo repo) {
            this.repo = repo;
        }

        @Override
        public void credit(Long accountId, BigDecimal amount) {
            Account existing = repo.store.get(accountId);
            if (existing == null) {
                throw new IllegalArgumentException("Account not found for id: " + accountId);
            }
            repo.update(new Account(existing.id(), existing.userId(), existing.name(), existing.balance().add(amount)));
        }
    }
}

