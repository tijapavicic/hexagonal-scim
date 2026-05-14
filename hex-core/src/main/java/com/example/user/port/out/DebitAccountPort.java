package com.example.user.port.out;

import java.math.BigDecimal;

public interface DebitAccountPort {

    void debit(Long accountId, BigDecimal amount);
}

