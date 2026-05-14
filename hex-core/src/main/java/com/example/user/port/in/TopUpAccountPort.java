package com.example.user.port.in;

import com.example.user.model.Account;

import java.math.BigDecimal;

public interface TopUpAccountPort {
    Account topUp(Long userId, Long accountId, BigDecimal amount);
}

