package com.example.user.adapter.db;

import com.example.user.core.InsufficientFundsException;
import com.example.user.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRepositoryAdapterTest {

    @Mock
    private AccountJpaRepository accountJpaRepository;

    private AccountRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountRepositoryAdapter(accountJpaRepository);
    }

    @Test
    void saveMapsBalance() {
        AccountEntity savedEntity = new AccountEntity(1L, "Main", new BigDecimal("25.50"));
        withId(savedEntity, 10L);
        when(accountJpaRepository.save(any(AccountEntity.class))).thenReturn(savedEntity);

        Account saved = adapter.save(new Account(null, 1L, "Main", new BigDecimal("25.50")));

        assertEquals(10L, saved.id());
        assertEquals(new BigDecimal("25.50"), saved.balance());
    }

    @Test
    void findAllByUserIdMapsBalance() {
        AccountEntity first = new AccountEntity(1L, "Main", new BigDecimal("10.00"));
        withId(first, 1L);
        AccountEntity second = new AccountEntity(1L, "Savings", new BigDecimal("20.00"));
        withId(second, 2L);
        when(accountJpaRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(first, second));

        List<Account> accounts = adapter.findAllByUserId(1L);

        assertEquals(2, accounts.size());
        assertEquals(new BigDecimal("10.00"), accounts.get(0).balance());
        assertEquals(new BigDecimal("20.00"), accounts.get(1).balance());
    }

    @Test
    void findByIdAndUserIdMapsBalance() {
        AccountEntity entity = new AccountEntity(1L, "Main", new BigDecimal("33.33"));
        withId(entity, 7L);
        when(accountJpaRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(entity));

        Optional<Account> account = adapter.findByIdAndUserId(7L, 1L);

        assertTrue(account.isPresent());
        assertEquals(new BigDecimal("33.33"), account.orElseThrow().balance());
    }

    @Test
    void debitUsesAtomicUpdate() {
        when(accountJpaRepository.debitIfEnough(5L, new BigDecimal("5.00"))).thenReturn(1);

        adapter.debit(5L, new BigDecimal("5.00"));

        verify(accountJpaRepository).debitIfEnough(5L, new BigDecimal("5.00"));
    }

    @Test
    void debitThrowsWhenInsufficientOrAccountMissing() {
        when(accountJpaRepository.debitIfEnough(5L, new BigDecimal("500.00"))).thenReturn(0);

        assertThrows(InsufficientFundsException.class,
                () -> adapter.debit(5L, new BigDecimal("500.00")));
    }

    @Test
    void creditUsesAtomicUpdate() {
        when(accountJpaRepository.credit(5L, new BigDecimal("7.00"))).thenReturn(1);

        adapter.credit(5L, new BigDecimal("7.00"));

        verify(accountJpaRepository).credit(5L, new BigDecimal("7.00"));
    }

    @Test
    void creditThrowsWhenAccountMissing() {
        when(accountJpaRepository.credit(99L, new BigDecimal("1.00"))).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> adapter.credit(99L, new BigDecimal("1.00")));
    }

    @Test
    void debitRejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> adapter.debit(1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> adapter.debit(1L, new BigDecimal("-1.00")));
    }

    @Test
    void creditRejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> adapter.credit(1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> adapter.credit(1L, new BigDecimal("-1.00")));
    }

    private static void withId(AccountEntity entity, Long id) {
        try {
            var idField = AccountEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set AccountEntity id in test", ex);
        }
    }
}

