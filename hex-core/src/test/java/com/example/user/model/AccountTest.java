package com.example.user.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    @Test
    void constructorDefaultsBalanceToZeroWhenNull() {
        Account account = new Account(1L, 1L, "Main", null);

        assertEquals(BigDecimal.ZERO, account.balance());
    }

    @Test
    void constructorRejectsNegativeBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, 1L, "Main", new BigDecimal("-0.01")));
    }

    @Test
    void convenienceConstructorSetsZeroBalance() {
        Account account = new Account(1L, 1L, "Main");

        assertEquals(BigDecimal.ZERO, account.balance());
    }
}

